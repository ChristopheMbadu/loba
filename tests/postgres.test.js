// Optional real-database integration test. Use a dedicated EMPTY disposable database only.
import test from 'node:test';
import assert from 'node:assert/strict';
import { Store } from '../server/store.js';

test('PostgreSQL bootstrap, transactions, snapshots, conflicts and deletion', {skip:!process.env.TEST_DATABASE_URL}, async()=>{
  const {default:pg}=await import('pg');
  const pool=new pg.Pool({connectionString:process.env.TEST_DATABASE_URL});
  try {
    const existing=await pool.query("SELECT tablename FROM pg_tables WHERE schemaname='public'");
    assert.equal(existing.rows.length,0,'TEST_DATABASE_URL must point at an empty disposable database.');
    const store=new Store(pool);await store.initialize({email:'test@example.com',password:'temporary-test-password'});
    assert.equal((await store.lessons()).length,24);
    await store.initialize({});assert.equal((await store.lessons()).length,24,'Reboot should not reseed');
    const original=(await store.lessons())[0];const content={...original.content,title:'Private change'};
    const saved=await store.mutate({id:original.id,content,version:original.version,action:'saved',email:'test@example.com'});
    assert.equal(saved.version,2);assert.equal((await store.published()).find(r=>r.id===original.id).content.title,original.content.title);
    await assert.rejects(store.mutate({id:original.id,content,version:1,action:'saved',email:'test@example.com'}),{status:409});
    const live=await store.mutate({id:original.id,version:saved.version,action:'published',email:'test@example.com'});assert.equal(live.published.title,'Private change');
    const draft=await store.mutate({id:original.id,version:live.version,action:'unpublished',email:'test@example.com'});
    await store.mutate({id:original.id,version:draft.version,action:'deleted',email:'test@example.com'});
    await store.initialize({});assert.equal((await store.lessons()).length,23,'Deleted seed lessons must not return on reboot');
    for(let i=0;i<10;i++)assert.ok(await store.loginAllowed('test-key'));assert.equal(await store.loginAllowed('test-key'),false);
    assert.equal((await store.activity()).length,4);
    assert.equal((await store.admin('test@example.com')).approved,true,'Existing administrators retain access');
    await store.register('new@example.com','temporary-signup-password');
    const pending=await store.admin('new@example.com');
    assert.equal(pending.approved,false);
    await assert.rejects(store.register('new@example.com','different-signup-password'),{status:409});
    const accounts=await store.pendingAccounts();
    assert.equal(accounts.length,1); assert.equal(accounts[0].password_hash,undefined);
    await store.createSession('pending-test-session',pending.id);
    assert.equal(await store.session('pending-test-session'),undefined);
    await store.approveAccount(pending.id,'test@example.com');
    assert.equal((await store.session('pending-test-session')).email,'new@example.com');
    assert.equal((await store.pendingAccounts()).length,0);
    await assert.rejects(store.approveAccount(pending.id,'test@example.com'),{status:404});
  }finally{await pool.end()}
});
