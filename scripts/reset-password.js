import pg from 'pg';
import readline from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import { hashPassword } from '../server/security.js';
import { randomUUID } from 'node:crypto';

const create = process.argv.includes('--create');
if (!stdin.isTTY) throw new Error('Account setup requires an interactive terminal.');
if (!process.env.DATABASE_URL) throw new Error('Set DATABASE_URL first.');
const rl=readline.createInterface({input:stdin,output:stdout});
const email=(await rl.question(create ? 'New administrator email: ' : 'Existing editor email: ')).trim().toLowerCase();
rl.close();
if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error('Enter a valid email address.');
function secret(prompt) {
  stdout.write(prompt); stdin.setRawMode(true); stdin.resume();
  return new Promise((resolve,reject)=>{
    let value='';
    const finish=()=>{stdin.off('data',onData);stdin.setRawMode(false);stdin.pause();stdout.write('\n')};
    function onData(buffer) {
      for(const char of buffer.toString()) {
        if(char==='\u0003'){finish();reject(new Error('Cancelled'));return}
        if(char==='\r'||char==='\n'){finish();resolve(value);return}
        if(char==='\u007f'||char==='\b')value=value.slice(0,-1);
        else if(char>=' ')value+=char;
      }
    }
    stdin.on('data',onData);
  });
}
const password=await secret('New password (hidden, at least 14 characters): ');
const confirmation=await secret('Confirm new password: ');
if(password!==confirmation || password.length<14 || password.length>256)throw new Error('Passwords must match and contain 14–256 characters.');
const pool=new pg.Pool({connectionString:process.env.DATABASE_URL});
const db=await pool.connect();
try {
  await db.query('BEGIN');
  if(create) {
    await db.query('INSERT INTO studio_admins(id,email,password_hash) VALUES($1,$2,$3)', [randomUUID(),email,await hashPassword(password)]);
  } else {
    const {rows}=await db.query('UPDATE studio_admins SET password_hash=$2 WHERE email=$1 RETURNING id',[email,await hashPassword(password)]);
    if(!rows.length)throw new Error('No editor exists with that email.');
    await db.query('DELETE FROM studio_sessions WHERE admin_id=$1',[rows[0].id]);
  }
  await db.query('COMMIT');console.log(create ? 'Administrator account created.' : 'Password updated. All existing sessions for this editor were revoked.');
} catch(error){await db.query('ROLLBACK');throw error}
finally{db.release();await pool.end()}
