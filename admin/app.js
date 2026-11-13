const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const names = {ln:'Lingala',sw:'Swahili'};
const regions = {ln:'Kinshasa, Brazzaville & the diaspora',sw:'East Africa & eastern Congo'};
const state = {lessons:[],activity:[],view:'overview',status:'all',language:'all',search:'',editing:null,dirty:false,busy:false};
let toastTimer;
function toast(message) { $('toast').textContent=message; $('toast').hidden=false; clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('toast').hidden=true,4000); }
async function api(path, options={}) {
  const response=await fetch(path,{credentials:'same-origin',...options,headers:{'Content-Type':'application/json',...options.headers}});
  let data; try { data=await response.json(); } catch { throw new Error('The server did not return a valid response. Please try again.'); }
  if(!response.ok) {
    if(response.status===401 && path!=='/api/auth/login') { $('editor').close(); $('preview').close(); showLogin(); }
    throw new Error(data.error || 'Request failed.');
  }
  return data;
}
function showLogin() { $('loading').hidden=true; $('workspace').hidden=true; $('login').hidden=false; const signup=location.pathname.replace(/\/$/,'')==='/signup'; $('login-form').hidden=signup; $('signup-form').hidden=!signup; }
function showWorkspace(user) { $('loading').hidden=true; $('login').hidden=true; $('workspace').hidden=false; $('user-email').textContent=user.email; $('avatar').textContent=user.email[0].toUpperCase(); }
function status(row) { return !row.published?'draft':row.version!==row.published_version?'changes':'published'; }
function badge(row) { const s=status(row); return `<span class="badge status-${s}">${{draft:'Draft',changes:'Unpublished changes',published:'Published'}[s]}</span>`; }
function date(value) { return value?new Intl.DateTimeFormat('en',{month:'short',day:'numeric'}).format(new Date(value)):'—'; }
function relative(value) { const n=Math.max(0,Date.now()-new Date(value)); return n<60000?'Just now':n<3600000?`${Math.floor(n/60000)}m ago`:n<86400000?`${Math.floor(n/3600000)}h ago`:date(value); }
async function refresh() {
  try {
    const data=await api('/api/admin/lessons'); state.lessons=data.lessons; state.activity=data.activity;
    $('load-error').hidden=true; render();
  } catch(error) { $('load-error').querySelector('span').textContent=error.message; $('load-error').hidden=false; }
}
function navigate(view) {
  if(view==='accounts')refreshAccounts();
  state.view=view;
  for(const section of document.querySelectorAll('.view')) section.hidden=section.id!==view+'-view';
  document.querySelectorAll('[data-view]').forEach(button=>{button.classList.toggle('active',button.dataset.view===view); if(button.dataset.view===view)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current')});
  const titles={overview:['Overview','Manage your curriculum and review recent changes.'],lessons:['Lessons','Create, refine, and publish your Lingala and Swahili curriculum.'],languages:['Languages','Thoughtful learning paths, rooted in culture.'],activity:['Activity','A record of what your editorial team has created, updated, and published.']};
  titles.accounts=['Accounts','Review new accounts and grant access to the lesson workspace.'];
  $('breadcrumb').textContent=view[0].toUpperCase()+view.slice(1); $('page-title').textContent=titles[view][0]; $('page-subtitle').textContent=titles[view][1];
  render(); window.scrollTo({top:0,behavior:'smooth'});
}
function languageCard(code, full=false) {
  const lessons=state.lessons.filter(l=>l.content.language===code), published=lessons.filter(l=>l.published).length;
  const words=lessons.reduce((n,l)=>n+l.content.words.length,0);
  if(full) return `<article class="card card-body language-full"><span class="language-icon ${code}">${code==='ln'?'🌿':'☀️'}</span><p class="eyebrow">${code==='ln'?'THE HEART OF CONGO':'A WORLD OF CONNECTIONS'}</p><h2>${names[code]}</h2><p class="muted">${regions[code]}</p><div class="language-numbers"><div><b>${lessons.length}</b><small>Lesson units</small></div><div><b>${published}</b><small>Published</small></div><div><b>${words}</b><small>Words & phrases</small></div></div><button class="button btn btn-primary" data-language="${code}">Manage ${names[code]} →</button></article>`;
  return `<article class="language-card"><span class="language-icon ${code}">${code==='ln'?'🌿':'☀️'}</span><div class="language-info"><h3>${names[code]}</h3><p>${lessons.length} lessons <span>·</span> ${published} published</p><div class="progress-track"><progress value="${published}" max="${Math.max(lessons.length,1)}" aria-label="${names[code]} published lessons"></progress></div></div><button class="icon-button btn btn-icon btn-ghost-secondary" data-language="${code}" aria-label="Manage ${names[code]}">↗</button></article>`;
}
function activities(items) {
  if(!items.length) return '<div class="activity-empty"><b>No recent activity.</b>Your existing curriculum is ready. Create, edit, or publish a lesson and your activity will appear here.</div>';
  return items.map(item=>`<div class="activity-item"><span class="activity-dot">${item.action==='published'?'↗':item.action==='created'?'+':'↺'}</span><div><p><b>${esc(item.lesson_title)}</b> ${esc(item.action)}.</p><small>${esc(item.admin_email)} · ${relative(item.created_at)}</small></div></div>`).join('');
}
function table(rows) {
  if(!rows.length) return `<div class="empty-state"><span>▤</span><h3>No lessons here yet.</h3><p>Try a different filter or create the next lesson.</p><button class="button btn btn-outline-secondary new-lesson">＋ Create lesson</button></div>`;
  return `<div class="table-responsive"><table class="table table-vcenter table-hover card-table"><thead><tr><th>Lesson</th><th>Language</th><th>Status</th><th>Vocabulary</th><th>Updated</th><th><span aria-label="Edit">↗</span></th></tr></thead><tbody>${rows.map(row=>`<tr><td><button class="lesson-edit" data-edit="${esc(row.id)}"><span class="lesson-name"><span class="lesson-icon">${esc(row.content.icon)}</span><span><b>${esc(row.content.title)}</b><small>UNIT ${String(row.content.position).padStart(2,'0')} · ${row.content.sentences.length} sentence exercises</small></span></span></button></td><td><span class="language-tag">${names[row.content.language]}</span></td><td>${badge(row)}</td><td>${row.content.words.length} words</td><td class="muted">${date(row.updated_at)}</td><td><button class="icon-button btn btn-icon btn-ghost-secondary" data-edit="${esc(row.id)}" aria-label="Edit ${esc(row.content.title)}">↗</button></td></tr>`).join('')}</tbody></table></div>`;
}
function render() {
  const all=state.lessons;
  $('nav-count').textContent=all.length;
  const values=[['Total lessons',all.length,'Across both learning paths','▤'],['Published',all.filter(l=>l.published).length,'Available in the content API','↗'],['In progress',all.filter(l=>status(l)!=='published').length,'Drafts & unpublished changes','◷'],['Words & phrases',all.reduce((n,l)=>n+l.content.words.length,0),'A growing world of vocabulary','✳']];
  $('stats').innerHTML=values.map(([label,value,sub,icon])=>`<article class="stat card card-body"><div class="stat-top">${label}</div><b>${value}</b><small>${sub}</small></article>`).join('');
  $('language-cards').innerHTML=['ln','sw'].map(c=>languageCard(c)).join('');
  $('language-detail').innerHTML=['ln','sw'].map(c=>languageCard(c,true)).join('');
  $('recent-activity').innerHTML=activities(state.activity.slice(0,3)); $('activity-list').innerHTML=activities(state.activity);
  $('recent-table').innerHTML=table([...all].sort((a,b)=>new Date(b.updated_at)-new Date(a.updated_at)).slice(0,4));
  const query=state.search.toLowerCase().trim();
  const filtered=all.filter(row=>(state.language==='all'||row.content.language===state.language) && (state.status==='all'||state.status==='published'&&!!row.published||status(row)===state.status) && (!query||[row.content.title,row.content.titleEn,...row.content.words.flatMap(w=>[w.term,w.fr,w.en])].join(' ').toLowerCase().includes(query)));
  $('lessons-table').innerHTML=table(filtered);
  $('language-filter').value=state.language;
}
let recorder=null, player=null;
async function uploadAudio(blob) {
  if(blob.size>1500000) throw new Error('This recording is too large. Keep it under 1.5 MB.');
  const response=await fetch('/api/admin/audio',{method:'POST',credentials:'same-origin',headers:{'Content-Type':blob.type||'application/octet-stream'},body:blob});
  const data=await response.json().catch(()=>({})); if(!response.ok) throw new Error(data.error||'Upload failed.'); return data.id;
}
function audioControls(row) {
  const input=row.querySelector('[data-field="audio"]'), status=row.querySelector('.audio-status');
  const [record,upload,play,clear]=['record','upload','play','clear'].map(name=>row.querySelector(`.audio-${name}`)), file=row.querySelector('.audio-file');
  const paint=message=>{const has=!!input.value;play.hidden=clear.hidden=!has;status.textContent=message??(has?'Recording attached':'No recording yet');status.classList.toggle('has-audio',has&&!message)};
  const attach=async blob=>{paint('Uploading…');try{input.value=await uploadAudio(blob);state.dirty=true;paint()}catch(error){paint(error.message)}};
  record.onclick=async()=>{
    if(recorder){recorder.stop();return}
    if(!navigator.mediaDevices||!window.MediaRecorder){paint('Recording is not supported in this browser. Upload a file instead.');return}
    try{
      const stream=await navigator.mediaDevices.getUserMedia({audio:true}); const chunks=[]; const active=recorder=new MediaRecorder(stream);
      active.ondataavailable=event=>chunks.push(event.data);
      active.onstop=()=>{stream.getTracks().forEach(track=>track.stop());recorder=null;record.textContent='● Record';record.classList.remove('recording');attach(new Blob(chunks,{type:active.mimeType}))};
      active.start(); record.textContent='■ Stop'; record.classList.add('recording'); paint('Recording… say the word clearly');
      setTimeout(()=>{if(recorder===active)active.stop()},15000);
    }catch{paint('Microphone access was blocked. Allow it, or upload a file.')}
  };
  upload.onclick=()=>file.click();
  file.onchange=()=>{if(file.files[0])attach(file.files[0]);file.value=''};
  play.onclick=()=>{player?.pause();player=new Audio(`/api/v1/audio/${input.value}`);player.play().catch(()=>paint('This recording could not be played.'))};
  clear.onclick=()=>{input.value='';state.dirty=true;paint()};
  paint();
}
function wordRow(word={term:'',fr:'',en:''}) {
  const row=document.createElement('div'); row.className='vocab-row';
  row.innerHTML=`<label>WORD / PHRASE<input class="form-control" data-field="term" value="${esc(word.term)}" maxlength="300" required placeholder="Mbote"></label><label>FRENCH<input class="form-control" data-field="fr" value="${esc(word.fr)}" maxlength="300" required placeholder="Bonjour"></label><label>ENGLISH<input class="form-control" data-field="en" value="${esc(word.en)}" maxlength="300" placeholder="Hello"></label><button type="button" class="icon-button btn btn-icon btn-ghost-secondary remove-row" aria-label="Remove vocabulary entry">×</button><div class="audio-row"><input type="hidden" data-field="audio" value="${esc(word.audio||'')}"><button type="button" class="btn btn-sm btn-outline-danger audio-record">● Record</button><button type="button" class="btn btn-sm btn-outline-secondary audio-upload">Upload audio</button><input type="file" class="audio-file" accept="audio/*" hidden><button type="button" class="btn btn-sm btn-outline-primary audio-play" hidden>▶ Listen</button><button type="button" class="btn btn-sm btn-ghost-secondary audio-clear" hidden>Remove recording</button><span class="audio-status muted" role="status"></span></div>`;
  $('word-rows').append(row); audioControls(row);
}
function sentenceRow(sentence={fr:'',en:'',answer:'',distractors:[]}) {
  const row=document.createElement('div'); row.className='sentence-row';
  row.innerHTML=`<div class="field-grid two"><label>French prompt<input class="form-control" data-field="fr" value="${esc(sentence.fr)}" required maxlength="600" placeholder="Je vais bien"></label><label>English prompt<input class="form-control" data-field="en" value="${esc(sentence.en)}" maxlength="600" placeholder="I am well"></label></div><div class="field-grid two"><label>Correct answer<input class="form-control" data-field="answer" value="${esc(sentence.answer)}" required maxlength="600" placeholder="Nazali malamu"></label><div class="sentence-bottom"><label>Distractors (comma-separated)<input class="form-control" data-field="distractors" value="${esc(sentence.distractors.join(', '))}" placeholder="Ozali, yo"></label><button type="button" class="icon-button btn btn-icon btn-ghost-secondary remove-row" aria-label="Remove sentence">×</button></div></div>`;
  $('sentence-rows').append(row);
}
function openEditor(id) {
  const row=state.lessons.find(l=>l.id===id); state.editing=row || null; state.dirty=false;
  const language=state.language==='all'?'ln':state.language;
  const content=row?.content||{language,position:Math.max(0,...state.lessons.filter(l=>l.content.language===language).map(l=>l.content.position))+1,title:'',titleEn:'',icon:'📚',tip:'',words:[],sentences:[]};
  for(const name of ['language','position','title','titleEn','icon','tip']) $('lesson-form').elements.namedItem(name).value=content[name];
  $('editor-heading').textContent=row?'Edit lesson':'Create a lesson';
  $('editor-eyebrow').textContent=row?`${names[content.language].toUpperCase()} · UNIT ${content.position}`:'NEW LESSON';
  $('editor-notice').textContent=row?.published?'This lesson is live. Saved edits stay private until you publish the new version.':'This lesson is a draft. Publish when it is ready for learners.';
  $('delete-lesson').hidden=!row; $('unpublish-lesson').hidden=!row?.published;
  $('word-rows').replaceChildren(); content.words.forEach(wordRow);
  $('sentence-rows').replaceChildren(); content.sentences.forEach(sentenceRow);
  $('editor-error').textContent=''; $('editor').showModal();
}
function readEditor() {
  const fields=new FormData($('lesson-form'));
  const pick=row=>Object.fromEntries([...row.querySelectorAll('[data-field]')].map(el=>[el.dataset.field,el.value.trim()]));
  return {language:fields.get('language'),position:Number(fields.get('position')),icon:fields.get('icon'),title:fields.get('title'),titleEn:fields.get('titleEn'),tip:fields.get('tip'),words:[...$('word-rows').children].map(pick),sentences:[...$('sentence-rows').children].map(row=>{const s=pick(row);s.distractors=s.distractors.split(',').map(t=>t.trim()).filter(Boolean);return s})};
}
function busy(value) { state.busy=value; $('lesson-form').querySelectorAll('button,input,select,textarea').forEach(el=>el.disabled=value); }
function savedRow(row) {
  state.editing=row; state.dirty=false;
  const i=state.lessons.findIndex(l=>l.id===row.id); if(i<0)state.lessons.push(row);else state.lessons[i]=row;
  $('delete-lesson').hidden=false; $('unpublish-lesson').hidden=!row.published;
}
async function save(publish=false) {
  if(state.busy || !$('lesson-form').reportValidity())return;
  const data=readEditor();
  if(publish&&!data.words.length) { $('editor-error').textContent='Add at least one vocabulary entry before publishing.'; return; }
  busy(true); $('editor-error').textContent='';
  try {
    let row=state.editing;
    if(!row||state.dirty) {
      const result=await api(row?`/api/admin/lessons/${row.id}`:'/api/admin/lessons',{method:row?'PUT':'POST',body:JSON.stringify({...data,version:row?.version})});
      row=result.lesson; savedRow(row);
    }
    if(publish) { const result=await api(`/api/admin/lessons/${row.id}/publish`,{method:'POST',body:JSON.stringify({version:row.version})}); savedRow(result.lesson); }
    $('editor').close(); toast(publish?'Lesson published. Ready for learners.':'Draft saved.'); await refresh();
  } catch(error) { $('editor-error').textContent=error.message; }
  finally { busy(false); }
}
async function mutateLesson(action) {
  if(state.busy || !state.editing)return;
  const row=state.editing;
  if(action==='delete'&&row.published) { $('editor-error').textContent='Unpublish this lesson before deleting it.';return; }
  const message=action==='delete'?`Delete “${row.content.title}”? This cannot be undone.`:`Unpublish “${row.content.title}”? It will no longer appear in the published content API.`;
  if(!confirm(message+(state.dirty?' Unsaved edits will be discarded.':'')))return;
  busy(true); $('editor-error').textContent='';
  try {
    await api(`/api/admin/lessons/${row.id}${action==='unpublish'?'/unpublish':''}`,{method:action==='delete'?'DELETE':'POST',body:JSON.stringify({version:row.version})});
    state.dirty=false; $('editor').close(); toast(action==='delete'?'Lesson deleted.':'Lesson returned to draft.'); await refresh();
  } catch(error) { $('editor-error').textContent=error.message; } finally {busy(false)}
}
function closeEditor() { if(state.busy)return; if(state.dirty&&!confirm('Discard your unsaved changes?'))return; state.dirty=false; $('editor').close(); }
function preview() {
  const content=readEditor();
  $('preview-content').innerHTML=`<div class="preview-title"><span>${esc(content.icon)}</span><p class="eyebrow">${names[content.language]} · UNIT ${content.position}</p><h2>${esc(content.title)}</h2><p class="muted">${esc(content.titleEn)}</p></div>${content.tip?`<div class="preview-note">${esc(content.tip)}</div>`:''}<h3>Words to know</h3>${content.words.length?content.words.map(w=>`<div class="preview-word"><b>${esc(w.term)}</b><span>${esc(w.fr)}${w.en?' · '+esc(w.en):''}</span></div>`).join(''):'<p class="muted">Add vocabulary to preview this lesson.</p>'}${content.sentences.length?'<h3>Try a sentence</h3>':''}${content.sentences.map(s=>`<div class="preview-word"><b>${esc(s.fr)}</b><span>${esc(s.en)}</span><details><summary>Reveal answer</summary><p>${esc(s.answer)}</p></details></div>`).join('')}`;
  $('preview').showModal();
}
async function refreshAccounts() {
  $('accounts-error').textContent=''; $('accounts-list').textContent='Loading accounts…';
  try {
    const {accounts}=await api('/api/admin/accounts');
    $('accounts-list').innerHTML=accounts.length?accounts.map(a=>`<div class="activity-item"><div><b>${esc(a.email)}</b><p class="muted">Requested ${date(a.created_at)}</p></div><button class="button btn btn-outline-secondary" data-approve="${esc(a.id)}" data-email="${esc(a.email)}">Approve access</button></div>`).join(''):'<p class="muted">No accounts awaiting approval.</p>';
  } catch(error) { $('accounts-list').textContent=''; $('accounts-error').textContent=error.message; }
}
$('refresh-accounts').onclick=refreshAccounts;
$('accounts-list').onclick=async event=>{
  const button=event.target.closest('[data-approve]'); if(!button)return;
  if(!confirm(`Give ${button.dataset.email} access to manage all lessons and approve accounts?`))return;
  button.disabled=true;
  try { await api(`/api/admin/accounts/${button.dataset.approve}/approve`,{method:'POST',body:'{}'}); toast('Account approved. They can now sign in.'); await refreshAccounts(); await refresh(); }
  catch(error) { $('accounts-error').textContent=error.message; button.disabled=false; }
};
$('signup-form').onsubmit=async event=>{
  event.preventDefault(); const form=event.currentTarget, button=form.querySelector('button');
  const fields=new FormData(form); $('signup-error').textContent='';
  if(fields.get('password')!==fields.get('confirmation')) { $('signup-error').textContent='Passwords do not match.'; return; }
  button.disabled=true;
  try {
    const result=await api('/api/auth/signup',{method:'POST',body:JSON.stringify({email:fields.get('email'),password:fields.get('password')})});
    form.reset(); form.querySelectorAll('label').forEach(el=>el.hidden=true); button.hidden=true;
    $('signup-success').textContent=result.message; $('signup-success').hidden=false;
  } catch(error) { $('signup-error').textContent=error.message; }
  finally { button.disabled=false; }
};
$('login-form').addEventListener('submit',async event=>{
  event.preventDefault(); const button=event.currentTarget.querySelector('button'); button.disabled=true; $('login-error').textContent='';
  try { const fields=new FormData(event.currentTarget);const {user}=await api('/api/auth/login',{method:'POST',body:JSON.stringify(Object.fromEntries(fields))}); $('login-form').reset(); showWorkspace(user); await refresh(); }
  catch(error){$('login-error').textContent=error.message}finally{button.disabled=false}
});
$('logout').onclick=async()=>{try{await api('/api/auth/logout',{method:'POST',body:'{}'});state.lessons=[];state.activity=[];showLogin()}catch(error){toast(error.message)}};
document.addEventListener('click',event=>{
  const button=event.target.closest('button');if(!button)return;
  if(button.dataset.view)navigate(button.dataset.view);
  if(button.classList.contains('new-lesson'))openEditor();
  if(button.dataset.edit)openEditor(button.dataset.edit);
  if(button.dataset.language){state.language=button.dataset.language;state.status='all';document.querySelectorAll('[data-status]').forEach(b=>b.classList.toggle('selected',b.dataset.status==='all'));navigate('lessons')}
  if(button.dataset.status){state.status=button.dataset.status;document.querySelectorAll('[data-status]').forEach(b=>b.classList.toggle('selected',b===button));render()}
  if(button.classList.contains('remove-row')){button.closest('.vocab-row,.sentence-row').remove();state.dirty=true}
});
$('search').oninput=event=>{state.search=event.target.value;render()};
$('language-filter').onchange=event=>{state.language=event.target.value;render()};
$('all-lessons').onclick=()=>navigate('lessons');
$('all-activity').onclick=()=>navigate('activity'); $('retry').onclick=refresh;
$('add-word').onclick=()=>{wordRow();state.dirty=true}; $('add-sentence').onclick=()=>{sentenceRow();state.dirty=true};
$('lesson-form').oninput=()=>state.dirty=true;
$('lesson-form').onsubmit=event=>{event.preventDefault();save()};
$('publish-lesson').onclick=()=>save(true); $('delete-lesson').onclick=()=>mutateLesson('delete'); $('unpublish-lesson').onclick=()=>mutateLesson('unpublish');
$('close-editor').onclick=closeEditor; $('editor').addEventListener('cancel',event=>{event.preventDefault();closeEditor()});
$('preview-lesson').onclick=preview; $('close-preview').onclick=()=>$('preview').close();
window.addEventListener('beforeunload',event=>{if(state.dirty){event.preventDefault();event.returnValue=''}});
(async()=>{if(location.pathname.replace(/\/$/,'')==='/signup'){showLogin();return}try{const {user}=await api('/api/auth/me');showWorkspace(user);await refresh()}catch(error){showLogin();if(error.message!=='Sign in to continue.')$('login-error').textContent=error.message}})();
