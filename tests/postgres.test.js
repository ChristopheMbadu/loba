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
    // Languages: seeded once, hidden ones keep their lessons out of the public feed.
    assert.deepEqual((await store.languages()).map(l=>l.code),['ln','sw']);
    const kg=await store.saveLanguage({language:{code:'kg',name_fr:'Kikongo',name_en:'Kikongo',greeting:'',cheer:'',region_fr:'',region_en:'',color:'#ff8a1f',position:3,published:false},email:'test@example.com'});
    assert.equal(kg.published,false);
    await assert.rejects(store.saveLanguage({language:{...kg},email:'test@example.com'}),{status:409});
    const unit=await store.mutate({id:'00000000-0000-4000-8000-000000000001',content:{language:'kg',position:1,title:'Mbote',titleEn:'',icon:'👋',tip:'',words:[{term:'Mbote',fr:'Bonjour',en:''}],sentences:[]},action:'created',email:'test@example.com'});
    await store.mutate({id:unit.id,version:unit.version,action:'published',email:'test@example.com'});
    assert.ok(!(await store.published()).some(r=>r.id===unit.id),'Hidden languages stay out of the public feed');
    await store.saveLanguage({language:{...kg,published:true},code:'kg',email:'test@example.com'});
    assert.ok((await store.published()).some(r=>r.id===unit.id));
    assert.deepEqual((await store.publishedLanguages()).map(l=>l.code),['ln','sw','kg']);
    await assert.rejects(store.deleteLanguage({code:'kg',email:'test@example.com'}),{status:409});
    await store.initialize({});assert.equal((await store.languages()).length,3,'Reboot keeps languages as edited');
    // Learner accounts and saved progress.
    const learner=await store.createLearner({email:'nadia@example.com',name:'Nadia',password:'kinshasa2026',state:{v:1,xp:10}});
    await assert.rejects(store.createLearner({email:'nadia@example.com',name:'N',password:'kinshasa2026'}),{status:409});
    await store.createLearnerSession('learner-session',learner.id);
    assert.equal((await store.learnerSession('learner-session')).name,'Nadia');
    assert.equal(await store.session('learner-session'),undefined,'Learner sessions never open Studio');
    await store.saveProgress(learner.id,{v:1,xp:50});assert.equal((await store.progress(learner.id)).state.xp,50);
    assert.equal(await store.learnerCount(),1);
    await store.saveProgress(learner.id,{v:1,lang:'ln',xp:'50',progress:{'ln-mbote':4},words:{'ln:Mbote':{level:2},'ln:Te':{level:0},bad:3}});
    const [row]=await store.learnerList();
    assert.equal(row.lang,'ln');assert.equal(row.xp,'50');assert.equal(row.words,1);assert.deepEqual(row.progress,{'ln-mbote':4});
    await store.deleteLearner(learner.id);
    assert.equal(await store.learnerSession('learner-session'),undefined);assert.equal(await store.progress(learner.id),null);
  }finally{await pool.end()}
});
