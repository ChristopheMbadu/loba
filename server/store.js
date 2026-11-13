import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { hashPassword, fail } from './security.js';

export class Store {
  constructor(pool) { this.pool = pool; }
  async initialize({ email, password }) {
    const db = await this.pool.connect();
    try {
      await db.query('BEGIN');
      await db.query('SELECT pg_advisory_xact_lock(742109)');
      await db.query(await readFile(new URL('./schema.sql', import.meta.url), 'utf8'));
      const { rows } = await db.query('SELECT id FROM studio_admins LIMIT 1');
      if (!rows.length) {
        if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || !password || password.length < 14 || password.length > 256) throw new Error('First startup requires ADMIN_EMAIL and a unique ADMIN_PASSWORD (14–256 characters).');
        await db.query('INSERT INTO studio_admins(id,email,password_hash) VALUES($1,$2,$3)', [randomUUID(), email.toLowerCase().trim(), await hashPassword(password)]);
      }
      const imported = await db.query("SELECT name FROM studio_migrations WHERE name='initial-curriculum'");
      if (!imported.rows.length) {
        const lessons = JSON.parse(await readFile(new URL('../data/lessons.json', import.meta.url), 'utf8'));
        for (const {id, ...content} of lessons) await db.query('INSERT INTO studio_lessons(id,content,published,published_version,published_at) VALUES($1,$2,$2,1,now()) ON CONFLICT(id) DO NOTHING', [id, JSON.stringify(content)]);
        await db.query("INSERT INTO studio_migrations(name) VALUES('initial-curriculum')");
      }
      // Recordings that no draft or published lesson references after a week were abandoned uploads.
      await db.query(`DELETE FROM studio_audio a WHERE a.created_at < now()-interval '7 days' AND NOT EXISTS
        (SELECT 1 FROM studio_lessons l WHERE l.content::text LIKE '%'||a.id::text||'%' OR l.published::text LIKE '%'||a.id::text||'%')`);
      await db.query('DELETE FROM studio_sessions WHERE expires_at < now()');
      await db.query('DELETE FROM studio_login_attempts WHERE resets_at < now()');
      await db.query('COMMIT');
    } catch (error) { await db.query('ROLLBACK'); throw error; }
    finally { db.release(); }
  }
  async healthy() { await this.pool.query('SELECT 1'); }
  async loginAllowed(key) {
    const {rows} = await this.pool.query(`INSERT INTO studio_login_attempts(key,attempts,resets_at) VALUES($1,1,now()+interval '15 minutes')
      ON CONFLICT(key) DO UPDATE SET attempts=CASE WHEN studio_login_attempts.resets_at < now() THEN 1 ELSE studio_login_attempts.attempts+1 END,
      resets_at=CASE WHEN studio_login_attempts.resets_at < now() THEN now()+interval '15 minutes' ELSE studio_login_attempts.resets_at END RETURNING attempts`, [key]);
    return rows[0].attempts <= 10;
  }
  async admin(email) { return (await this.pool.query('SELECT * FROM studio_admins WHERE email=$1', [email])).rows[0]; }
  async register(email, password) {
    const {rows}=await this.pool.query('INSERT INTO studio_admins(id,email,password_hash,approved) VALUES($1,$2,$3,FALSE) ON CONFLICT(email) DO NOTHING RETURNING id', [randomUUID(),email,await hashPassword(password)]);
    if(!rows.length) throw fail(409, 'An account already exists for this email. Sign in or contact your administrator.');
  }
  async pendingAccounts() { return (await this.pool.query('SELECT id,email,created_at FROM studio_admins WHERE approved=FALSE ORDER BY created_at')).rows; }
  async approveAccount(id,email) {
    const db=await this.pool.connect();
    try {
      await db.query('BEGIN');
      const {rows}=await db.query('UPDATE studio_admins SET approved=TRUE WHERE id=$1 AND approved=FALSE RETURNING email',[id]);
      if(!rows.length) throw fail(404,'Pending account not found.');
      await db.query('INSERT INTO studio_activity(admin_email,action,lesson_title) VALUES($1,$2,$3)',[email,'account approved',rows[0].email]);
      await db.query('COMMIT');
    } catch(error) { await db.query('ROLLBACK'); throw error; }
    finally { db.release(); }
  }
  async createSession(tokenHash, adminId) { await this.pool.query("INSERT INTO studio_sessions(token_hash,admin_id,expires_at) VALUES($1,$2,now()+interval '12 hours')", [tokenHash, adminId]); }
  async session(tokenHash) { return (await this.pool.query('SELECT a.id,a.email FROM studio_sessions s JOIN studio_admins a ON a.id=s.admin_id WHERE s.token_hash=$1 AND s.expires_at>now() AND a.approved=TRUE', [tokenHash])).rows[0]; }
  async logout(tokenHash) { await this.pool.query('DELETE FROM studio_sessions WHERE token_hash=$1', [tokenHash]); }
  async lessons() { return (await this.pool.query("SELECT * FROM studio_lessons ORDER BY content->>'language', (content->>'position')::int, id")).rows; }
  async published() { return (await this.pool.query("SELECT id,published AS content,published_version AS version,published_at FROM studio_lessons WHERE published IS NOT NULL ORDER BY published->>'language', (published->>'position')::int, id")).rows; }
  async saveAudio({ id, mime, data, email }) { await this.pool.query('INSERT INTO studio_audio(id,mime,data,admin_email) VALUES($1,$2,$3,$4)', [id, mime, data, email]); }
  async audio(id) { return (await this.pool.query('SELECT mime,data FROM studio_audio WHERE id=$1', [id])).rows[0]; }
  async activity() { return (await this.pool.query('SELECT * FROM studio_activity ORDER BY id DESC LIMIT 15')).rows; }
  async mutate({ id, content, version, action, email }) {
    const db = await this.pool.connect();
    try {
      await db.query('BEGIN');
      let row;
      if (action === 'created') {
        row = (await db.query('INSERT INTO studio_lessons(id,content) VALUES($1,$2) RETURNING *', [id, JSON.stringify(content)])).rows[0];
      } else {
        row = (await db.query('SELECT * FROM studio_lessons WHERE id=$1 FOR UPDATE', [id])).rows[0];
        if (!row) throw fail(404, 'Lesson not found.');
        if (!Number.isInteger(version) || row.version !== version) throw fail(409, 'This lesson changed in another window. Reload it before saving.');
        if (action === 'saved') row = (await db.query('UPDATE studio_lessons SET content=$2,version=version+1,updated_at=now() WHERE id=$1 RETURNING *', [id, JSON.stringify(content)])).rows[0];
        if (action === 'published') {
          if (!row.content.words.length) throw fail(400, 'Add at least one vocabulary entry before publishing.');
          row = (await db.query('UPDATE studio_lessons SET published=content,version=version+1,published_version=version+1,published_at=now(),updated_at=now() WHERE id=$1 RETURNING *', [id])).rows[0];
        }
        if (action === 'unpublished') row = (await db.query('UPDATE studio_lessons SET published=NULL,published_version=NULL,published_at=NULL,version=version+1,updated_at=now() WHERE id=$1 RETURNING *', [id])).rows[0];
        if (action === 'deleted') {
          if (row.published) throw fail(409, 'Unpublish this lesson before deleting it.');
          await db.query('DELETE FROM studio_lessons WHERE id=$1', [id]);
        }
      }
      await db.query('INSERT INTO studio_activity(admin_email,action,lesson_title) VALUES($1,$2,$3)', [email, action, row.content.title]);
      await db.query('COMMIT');
      return row;
    } catch (error) { await db.query('ROLLBACK'); throw error; }
    finally { db.release(); }
  }
}
