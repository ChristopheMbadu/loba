import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { readFile } from 'node:fs/promises';
import { createApp } from '../server/app.js';
import { hashPassword, verifyPassword, hashToken, validateLesson, sniffAudio, fail } from '../server/security.js';

const lesson={language:'ln',position:1,title:'Mbote !',titleEn:'Greetings',icon:'👋',tip:'Welcome',words:[{term:'Mbote',fr:'Bonjour',en:'Hello'}],sentences:[{fr:'Je vais bien',en:'I am well',answer:'Nazali malamu',distractors:['yo']}]};
const origin='https://studio.example.com';
async function invoke(app,{url='/',method='GET',headers={},data,raw}={}) {
  const payload=raw ?? (data===undefined?'':JSON.stringify(data));
  const req=Readable.from(payload?[Buffer.from(payload)]:[]);
  Object.assign(req,{url,method,headers,socket:{remoteAddress:'127.0.0.1'}});
  const result={headers:{},status:200};
  const res={setHeader(k,v){result.headers[k.toLowerCase()]=v},writeHead(status,values){result.status=status;for(const [k,v] of Object.entries(values||{}))this.setHeader(k,v)},end(value){result.raw=String(value??'');try{result.data=JSON.parse(result.raw)}catch{}}};
  await app(req,res); return result;
}
function fakeStore() {
  const lessons=new Map();const sessions=new Map();const activity=[];
  return {
    lessonsMap:lessons,sessions,allowed:true,
    async healthy(){},async loginAllowed(){return this.allowed},async admin(){return this.user},
    async createSession(hash,id){sessions.set(hash,{id,email:this.user.email})},async session(hash){return sessions.get(hash)},async logout(hash){sessions.delete(hash)},
    audioMap:new Map(),async saveAudio({id,mime,data}){this.audioMap.set(id,{mime,data})},async audio(id){return this.audioMap.get(id)},
    async lessons(){return [...lessons.values()]},async activity(){return activity},
    async published(){return [...lessons.values()].filter(r=>r.published).map(r=>({id:r.id,content:r.published,version:r.published_version}))},
    async mutate({id,content,action,version}){
      let row=lessons.get(id);
      if(action==='created'){row={id,content,version:1,published:null};lessons.set(id,row)}
      else{if(!row)throw fail(404,'Lesson not found.');if(version!==row.version)throw fail(409,'Conflict');
        if(action==='saved'){row.content=content;row.version++}
        if(action==='published'){if(!row.content.words.length)throw fail(400,'Add vocabulary');row.published=structuredClone(row.content);row.published_version=++row.version}
        if(action==='unpublished'){row.published=null;row.published_version=null;row.version++}
        if(action==='deleted'){if(row.published)throw fail(409,'Unpublish first');lessons.delete(id)}
      }return structuredClone(row);
    }
  };
}
function authenticated(){const store=fakeStore();store.sessions.set(hashToken('test-session'),{id:'editor',email:'editor@example.com'});return{store,app:createApp(store,{origin,production:true}),headers:{origin,'content-type':'application/json',cookie:'__Host-loba_session=test-session'}}}

test('public signup validates input, limits attempts and cannot grant access',async()=>{
  const {store,app,headers}=authenticated();
  let registered;
  store.register=async(email,password)=>{registered={email,password}};
  const data={email:' New@Example.com ',password:'temporary-test-password',approved:true};
  const signup={url:'/api/auth/signup',method:'POST',headers:{origin,'content-type':'application/json'}};
  assert.equal((await invoke(app,{...signup,data:{...data,password:'short'}})).status,400);
  assert.equal((await invoke(app,{...signup,data:{...data,email:'bad'}})).status,400);
  assert.equal((await invoke(app,{...signup,headers:{...signup.headers,origin:'https://attacker.example'},data})).status,403);
  const result=await invoke(app,{...signup,data});
  assert.equal(result.status,201); assert.equal(result.headers['set-cookie'],undefined);
  assert.deepEqual(registered,{email:'new@example.com',password:data.password});
  store.allowed=false; assert.equal((await invoke(app,{...signup,data})).status,429); store.allowed=true;
  store.user={id:'pending',email:'new@example.com',approved:false,password_hash:await hashPassword(data.password)};
  const login=await invoke(app,{url:'/api/auth/login',method:'POST',headers:signup.headers,data:{email:'new@example.com',password:data.password}});
  assert.equal(login.status,403); assert.equal(login.headers['set-cookie'],undefined);
  assert.equal((await invoke(app,{url:'/api/admin/accounts'})).status,401);
  assert.equal((await invoke(app,{url:'/api/admin/accounts/12345678-1234-1234-1234-123456789012/approve',method:'POST',headers:signup.headers,data:{}})).status,401);
  store.pendingAccounts=async()=>[{id:'pending',email:'new@example.com'}];
  assert.equal((await invoke(app,{url:'/api/admin/accounts',headers})).data.accounts.length,1);
  let approved=false;store.approveAccount=async()=>{approved=true};
  assert.equal((await invoke(app,{url:'/api/admin/accounts/12345678-1234-1234-1234-123456789012/approve',method:'POST',headers,data:{}})).status,200);
  assert.equal(approved,true);
  assert.equal((await invoke(app,{url:'/signup'})).status,200);
});

test('password hashing is salted and rejects incorrect credentials',async()=>{
  const password='a-unique-long-test-password';const first=await hashPassword(password),second=await hashPassword(password);
  assert.notEqual(first,second);assert.ok(await verifyPassword(password,first));assert.equal(await verifyPassword('wrong',first),false);assert.equal(await verifyPassword(password,undefined),false);
});
test('all 24 imported units validate, retain their original identity and have vocabulary',async()=>{
  const data=JSON.parse(await readFile(new URL('../data/lessons.json',import.meta.url)));
  assert.equal(data.length,24);assert.equal(new Set(data.map(l=>l.id)).size,24);
  for(const code of ['ln','sw'])assert.equal(data.filter(l=>l.language===code).length,12);
  for(const item of data){assert.ok(validateLesson(item).words.length);assert.ok(item.id.startsWith(item.language+'-'))}
});
test('content validation rejects malformed fields and excessive sizes',()=>{
  assert.throws(()=>validateLesson({...lesson,language:'xx'}),{status:400});
  assert.throws(()=>validateLesson({...lesson,title:' '}),{status:400});
  assert.throws(()=>validateLesson({...lesson,position:0}),{status:400});
  assert.throws(()=>validateLesson({...lesson,words:[null]}),{status:400});
  assert.throws(()=>validateLesson({...lesson,words:Array(101).fill(lesson.words[0])}),{status:400});
  assert.throws(()=>validateLesson({...lesson,sentences:[{...lesson.sentences[0],distractors:'bad'}]}),{status:400});
  assert.equal(validateLesson({...lesson,privilege:'admin'}).privilege,undefined);
});
test('admin endpoints require authentication and reject cross-origin writes',async()=>{
  const {app,headers}=authenticated();
  assert.equal((await invoke(app,{url:'/api/admin/lessons'})).status,401);
  assert.equal((await invoke(app,{url:'/api/admin/lessons',method:'POST',headers:{...headers,origin:'https://attacker.example'},data:lesson})).status,403);
  assert.equal((await invoke(app,{url:'/api/admin/lessons',method:'POST',headers:{...headers,'content-type':'text/plain'},data:lesson})).status,415);
});
test('login creates a secure opaque session, and logout revokes it',async()=>{
  const store=fakeStore();store.user={id:'admin-1',email:'editor@example.com',password_hash:await hashPassword('correct-test-password')};
  const app=createApp(store,{origin,production:true});const headers={origin,'content-type':'application/json'};
  const wrong=await invoke(app,{url:'/api/auth/login',method:'POST',headers,data:{email:store.user.email,password:'wrong'}});assert.equal(wrong.status,401);
  const result=await invoke(app,{url:'/api/auth/login',method:'POST',headers,data:{email:store.user.email,password:'correct-test-password'}});
  assert.equal(result.status,200);assert.match(result.headers['set-cookie'],/HttpOnly; SameSite=Strict; Max-Age=43200; Secure/);
  const cookie=result.headers['set-cookie'].split(';')[0];const token=cookie.split('=')[1];assert.equal(token.length,64);assert.ok(store.sessions.has(hashToken(token)));assert.ok(!store.sessions.has(token));
  assert.equal((await invoke(app,{url:'/api/auth/me',headers:{cookie}})).status,200);
  assert.equal((await invoke(app,{url:'/api/auth/logout',method:'POST',headers:{...headers,cookie},data:{}})).status,200);
  assert.equal((await invoke(app,{url:'/api/auth/me',headers:{cookie}})).status,401);
});
test('login is rate-limited before credential checks and validates null input',async()=>{
  const store=fakeStore();store.allowed=false;const app=createApp(store,{origin});const options={url:'/api/auth/login',method:'POST',headers:{origin,'content-type':'application/json'}};
  const limited=await invoke(app,{...options,data:{email:'test@example.com',password:'test'}});assert.equal(limited.status,429);assert.equal(limited.headers['retry-after'],'900');
  assert.equal((await invoke(app,{...options,data:null})).status,400);
});
test('draft/create/edit/publish/unpublish/delete API workflow preserves public snapshots',async()=>{
  const {app,headers}=authenticated();const request=(url,method,data)=>invoke(app,{url,method,headers,data});
  let result=await request('/api/admin/lessons','POST',lesson);assert.equal(result.status,201);const id=result.data.lesson.id;
  let current=result.data.lesson;
  assert.equal((await invoke(app,{url:'/api/v1/lessons'})).data.lessons.length,0);
  result=await request(`/api/admin/lessons/${id}/publish`,'POST',{version:current.version});current=result.data.lesson;assert.equal(result.status,200);
  const published=(await invoke(app,{url:'/api/v1/lessons'})).data.lessons[0];assert.equal(published.content.title,lesson.title);assert.equal(published.published,undefined);
  result=await request(`/api/admin/lessons/${id}`,'PUT',{...lesson,title:'Changed privately',version:current.version});current=result.data.lesson;
  assert.equal((await invoke(app,{url:'/api/v1/lessons'})).data.lessons[0].content.title,lesson.title);
  assert.equal((await request(`/api/admin/lessons/${id}`,'PUT',{...lesson,version:1})).status,409);
  assert.equal((await request(`/api/admin/lessons/${id}`,'DELETE',{version:current.version})).status,409);
  result=await request(`/api/admin/lessons/${id}/unpublish`,'POST',{version:current.version});current=result.data.lesson;
  assert.equal((await invoke(app,{url:'/api/v1/lessons'})).data.lessons.length,0);
  assert.equal((await request(`/api/admin/lessons/${id}`,'DELETE',{version:current.version})).status,200);
  assert.equal((await request('/api/admin/lessons','GET')).data.lessons.length,0);
});
test('invalid and oversized JSON requests are rejected',async()=>{
  const {app,headers}=authenticated();const options={url:'/api/admin/lessons',method:'POST',headers};
  assert.equal((await invoke(app,{...options,raw:'not json'})).status,400);
  assert.equal((await invoke(app,{...options,raw:' '.repeat(200001)})).status,413);
});
test('static serving exposes only allowlisted assets and includes restrictive headers',async()=>{
  const {app}=authenticated();const index=await invoke(app);assert.equal(index.status,200);assert.match(index.raw,/Loba Studio/);assert.match(index.headers['content-security-policy'],/script-src 'self'/);
  for(const path of ['/.env','/server/store.js','/data/lessons.json','/../.env','/sw.js'])assert.equal((await invoke(app,{url:path})).status,404,path);
  assert.equal((await invoke(app,{url:'/admin/app.js'})).status,200);
  assert.equal((await invoke(app,{url:'/admin/style.css'})).status,200);
});
test('dashboard element references match the HTML',async()=>{
  const html=await readFile(new URL('../admin/index.html',import.meta.url),'utf8');const js=await readFile(new URL('../admin/app.js',import.meta.url),'utf8');
  const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);assert.equal(new Set(ids).size,ids.length,'Duplicate IDs');
  for(const match of js.matchAll(/\$\('([^']+)'\)/g))assert.ok(ids.includes(match[1]),`Missing #${match[1]}`);
});

test('learner app is served as a single-page app from /learn without exposing other files',async()=>{
  const {mkdtemp,mkdir,writeFile}=await import('node:fs/promises');const {tmpdir}=await import('node:os');const {join}=await import('node:path');const {pathToFileURL}=await import('node:url');
  const dir=await mkdtemp(join(tmpdir(),'loba-learn-'));await mkdir(join(dir,'assets'));
  await writeFile(join(dir,'index.html'),'<!doctype html><div id="root"></div>');await writeFile(join(dir,'assets','index-AbC123.js'),'console.log(1)');
  await writeFile(join(dir,'assets','font-x.woff2'),'woff');await writeFile(join(dir,'manifest.webmanifest'),'{}');await writeFile(join(dir,'secret.txt'),'no');
  const app=createApp(fakeStore(),{origin,production:true,learnDir:pathToFileURL(dir+'/')});
  for(const url of ['/learn','/learn/','/learn/practice','/learn/lesson/ln-mbote?n=2']){
    const r=await invoke(app,{url});assert.equal(r.status,200,url);assert.ok(r.headers['content-type'].startsWith('text/html'));assert.match(r.raw,/id="root"/);
    assert.match(r.headers['content-security-policy'],/script-src 'self';/);assert.equal(r.headers['cache-control'],'no-store');
  }
  const js=await invoke(app,{url:'/learn/assets/index-AbC123.js'});assert.equal(js.status,200);assert.ok(js.headers['content-type'].startsWith('text/javascript'));assert.match(js.headers['cache-control'],/immutable/);
  assert.equal((await invoke(app,{url:'/learn/assets/font-x.woff2'})).headers['content-type'],'font/woff2');
  assert.equal((await invoke(app,{url:'/learn/manifest.webmanifest'})).status,200);
  for(const url of ['/learn/secret.txt','/learn/assets/missing.js','/learn/assets/.hidden.js','/learn/assets/x.txt','/learn/assets/%2e%2e%2fsecret.txt','/learn/../server/store.js','/learn/server/store.js','/learn/.env','/learn/package.json','/learner.js','/lottie-parrot.json'])
    assert.equal((await invoke(app,{url})).status,404,url);
  const unbuilt=createApp(fakeStore(),{origin,production:true,learnDir:pathToFileURL(join(dir,'missing')+'/')});
  assert.equal((await invoke(unbuilt,{url:'/learn'})).status,503);
});

test('learner scripts parse and animations need no remote images',async()=>{
  const {Script}=await import('node:vm');
  const html=await readFile(new URL('../index.html',import.meta.url),'utf8');
  for(const m of html.matchAll(/<script>([\s\S]*?)<\/script>/g))assert.doesNotThrow(()=>new Script(m[1]));
  for(const file of ['lottie-parrot.json','lottie-celebrate.json']){
    const animation=JSON.parse(await readFile(new URL('../'+file,import.meta.url)));
    assert.ok(animation.layers.length>0);assert.ok(animation.fr>0);
    assert.ok(animation.assets.every(a=>!a.p));
  }
});

test('teachers upload recordings that learners can stream; other content is refused',async()=>{
  const {app,headers}=authenticated();
  const webm=Buffer.concat([Buffer.from([0x1a,0x45,0xdf,0xa3]),Buffer.alloc(60,7)]);
  const upload=(raw,extra={})=>invoke(app,{url:'/api/admin/audio',method:'POST',headers:{...headers,'content-type':'audio/webm',...extra},raw});
  const made=await upload(webm);assert.equal(made.status,201);assert.match(made.data.id,/^[0-9a-f-]{36}$/);
  assert.equal((await upload(Buffer.from('<script>alert(1)</script> not audio'))).status,415);
  assert.equal((await upload(Buffer.alloc(1_600_000,1))).status,413);
  assert.equal((await upload(webm,{cookie:''})).status,401);
  assert.equal((await upload(webm,{origin:'https://evil.example'})).status,403);
  const full=await invoke(app,{url:`/api/v1/audio/${made.data.id}`});
  assert.equal(full.status,200);assert.equal(full.headers['content-type'],'audio/webm');assert.match(full.headers['cache-control'],/immutable/);assert.equal(full.headers['x-content-type-options'],'nosniff');
  const part=await invoke(app,{url:`/api/v1/audio/${made.data.id}`,headers:{range:'bytes=0-9'}});
  assert.equal(part.status,206);assert.equal(part.headers['content-range'],'bytes 0-9/64');assert.equal(part.headers['content-length'],10);
  assert.equal((await invoke(app,{url:`/api/v1/audio/${made.data.id}`,headers:{range:'bytes=900-'}})).status,416);
  assert.equal((await invoke(app,{url:'/api/v1/audio/00000000-0000-4000-8000-000000000000'})).status,404);
  const saved=validateLesson({...lesson,words:[{term:'Mbote',fr:'Bonjour',audio:made.data.id},{term:'Te',fr:'Non',audio:''}]});
  assert.equal(saved.words[0].audio,made.data.id);assert.ok(!('audio' in saved.words[1]));
  assert.throws(()=>validateLesson({...lesson,words:[{term:'Mbote',fr:'Bonjour',audio:'../../etc/passwd'}]}),{status:400});
  assert.equal(sniffAudio(Buffer.from('ID3\x04\x00\x00\x00\x00\x00\x00\x00\x00')),'audio/mpeg');
  assert.equal(sniffAudio(Buffer.from('\x00\x00\x00\x20ftypM4A \x00\x00')),'audio/mp4');
});
