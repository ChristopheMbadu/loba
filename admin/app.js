const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon = name => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
const SWATCHES = ['#1c9bef','#2bb673','#ef4e4e','#e0a100','#ff8a1f','#8b5cf6','#0fa3b1','#8f4f1c'];
const state = {lessons:[],languages:[],activity:[],learners:0,learnerRows:null,learnerSearch:'',learnerLanguage:'all',learnerSort:'recent',pending:0,view:'overview',status:'all',language:'all',search:'',editing:null,editingLanguage:null,dirty:false,busy:false};
let toastTimer;

function toast(message) { $('toast').textContent=message; $('toast').hidden=false; clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('toast').hidden=true,3500); }
async function api(path, options={}) {
  const response=await fetch(path,{credentials:'same-origin',...options,headers:{'Content-Type':'application/json',...options.headers}});
  let data; try { data=await response.json(); } catch { throw new Error('The server did not return a valid response. Please try again.'); }
  if(!response.ok) {
    if(response.status===401 && path!=='/api/auth/login') { for(const d of document.querySelectorAll('dialog[open]')) d.close(); showLogin(); }
    throw new Error(data.error || 'Request failed.');
  }
  return data;
}
// The Studio CSP forbids inline style attributes, so per-item colours and widths are applied through the DOM.
function applyVars(root=document) {
  root.querySelectorAll('[data-c]').forEach(el=>el.style.setProperty('--c',el.dataset.c));
  root.querySelectorAll('[data-pct]').forEach(el=>el.style.setProperty('--pct',el.dataset.pct+'%'));
}
function showLogin() { $('loading').hidden=true; $('workspace').hidden=true; $('login').hidden=false; const signup=location.pathname.replace(/\/$/,'')==='/signup'; $('login-form').hidden=signup; $('signup-form').hidden=!signup; }
function showWorkspace(user) { $('loading').hidden=true; $('login').hidden=true; $('workspace').hidden=false; $('user-email').textContent=user.email; $('avatar').textContent=user.email[0].toUpperCase(); }

const lang = code => state.languages.find(l=>l.code===code) || {code,name_en:code.toUpperCase(),name_fr:code.toUpperCase(),color:'#98a2b3',published:false,position:999};
const langTag = code => { const l=lang(code); return `<span class="lang-tag" data-c="${esc(l.color)}">${esc(l.name_en)}</span>`; };
function status(row) { return !row.published?'draft':row.version!==row.published_version?'changes':'published'; }
const STATUS_LABEL = {draft:'Draft',changes:'Unpublished changes',published:'Published'};
const badge = row => { const s=status(row); return `<span class="badge ${s}">${STATUS_LABEL[s]}</span>`; };
function date(value) { return value?new Intl.DateTimeFormat('en',{month:'short',day:'numeric'}).format(new Date(value)):'—'; }
function relative(value) { const n=Math.max(0,Date.now()-new Date(value)); return n<60000?'Just now':n<3600000?`${Math.floor(n/60000)} min ago`:n<86400000?`${Math.floor(n/3600000)} h ago`:date(value); }
const plural = (n, one, many=one+'s') => `${n} ${n===1?one:many}`;
const byCurriculum = (a,b) => lang(a.content.language).position-lang(b.content.language).position || a.content.language.localeCompare(b.content.language) || a.content.position-b.content.position;

// Consistent vector artwork for curriculum topics; stored lesson content stays intact.
const TOPIC_ICONS = {
  '👋':'M4 4h16v12H9l-5 4V4Z M8 8h8 M8 12h5',
  '🆘':'M9 9a3 3 0 0 1 6 0c0 2-3 2-3 4 M12 17h.01 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
  '👪':'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  '🍲':'M3 12h18a9 9 0 0 1-18 0Z M8 3v5 M12 2v6 M16 3v5 M7 21h10',
  '🔢':'M4 5h4v6 M4 11h8 M15 5h4v3l-4 3h4 M4 16h16 M8 14v6 M16 14v6',
  '🏃🏾':'M14 5a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M7 10l4-3 4 4 4 1 M11 7l-2 7 5 3v5 M9 14l-3 6H2',
  '🏠':'M3 10l9-7 9 7 M5 9v12h14V9 M9 21v-8h6v8',
  '🚕':'M5 11l2-7h10l2 7 M3 11h18v8H3v-8Z M5 19v2 M19 19v2 M6 15h2 M16 15h2',
  '⏰':'M12 7v5l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  '🩺':'M9 3h6v6h6v6h-6v6H9v-6H3V9h6V3Z',
  '💵':'M5 7h14l2 14H3L5 7Z M9 8V6a3 3 0 0 1 6 0v2',
  '💛':'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8'
};
// The pictures editors can pick for a unit. The emoji is what lessons store; Studio draws the matching icon.
const TOPICS = [['👋','Greetings'],['🆘','Getting by'],['👪','Family'],['🍲','Food'],['🔢','Numbers'],['🏃🏾','Actions'],['🏠','Home'],['🚕','Travel'],['⏰','Time'],['🩺','Health'],['💵','Shopping'],['💛','Feelings'],['📚','General']];
function lessonIcon(content) {
  const d=TOPIC_ICONS[content.icon] || 'M12 5C8 3 5 3 2 4v16c3-1 6-1 10 1 4-2 7-2 10-1V4c-3-1-6-1-10 1Z M12 5v16';
  return `<span class="lesson-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="${d}"/></svg></span>`;
}

// ---------- data ----------
async function refresh() {
  try {
    const data=await api('/api/admin/lessons');
    Object.assign(state,{lessons:data.lessons,languages:data.languages,activity:data.activity,learners:data.learners});
    $('load-error').hidden=true; render();
  } catch(error) { $('load-error').querySelector('span').textContent=error.message; $('load-error').hidden=false; }
}

// ---------- navigation ----------
const VIEWS = {
  overview:['Overview','Your curriculum at a glance.','lesson'],
  lessons:['Lessons','Write, review and publish units for each language.','lesson'],
  languages:['Languages','Every language Loba teaches, and whether learners can see it.','language'],
  learners:['Learners','Everyone who created an account in the learner app, and how far they’ve got.',null],
  accounts:['Team','People who can edit lessons in Studio.',null],
  activity:['Activity','What your team has created, edited and published.',null],
};
function navigate(view) {
  state.view=view;
  if(view==='accounts') refreshAccounts();
  if(view==='learners') loadLearners();
  for(const section of document.querySelectorAll('.view')) section.hidden=section.id!==view+'-view';
  document.querySelectorAll('.nav-item').forEach(b=>{ const on=b.dataset.view===view; b.classList.toggle('active',on); if(on)b.setAttribute('aria-current','page'); else b.removeAttribute('aria-current'); });
  const [title,subtitle,action]=VIEWS[view];
  $('page-title').textContent=title; $('page-subtitle').textContent=subtitle; document.title=`${title} · Loba Studio`;
  $('primary-action').hidden=!action; $('primary-label').textContent=action==='language'?'Add language':'New lesson';
  render(); window.scrollTo({top:0});
}

// ---------- rendering ----------
function languageSummary() {
  if(!state.languages.length) return `<div class="empty"><b>No languages yet.</b><span>Add the first language Loba should teach.</span></div>`;
  return state.languages.map(l=>{
    const lessons=state.lessons.filter(x=>x.content.language===l.code), live=lessons.filter(x=>x.published).length;
    const pct=lessons.length?Math.round(live/lessons.length*100):0;
    return `<button class="lang-row" data-filter-language="${esc(l.code)}"><span class="lang-swatch" data-c="${esc(l.color)}">${esc(l.code)}</span><span class="lang-name"><b>${esc(l.name_en)}</b><small>${plural(lessons.length,'unit')} · ${esc(l.region_en||l.name_fr)}</small></span><span class="meter-wrap"><span class="meter" data-c="${esc(l.color)}"><i data-pct="${pct}"></i></span><span class="meter-label">${live} of ${lessons.length} published</span></span><span class="badge ${l.published?'visible':'hidden'}">${l.published?'Visible':'Hidden'}</span></button>`;
  }).join('');
}
const ACTIVITY = {created:['plus','created'],saved:['edit','edited'],published:['send','published'],unpublished:['refresh','unpublished'],deleted:['trash','deleted'],'account approved':['users','was approved for Studio'],'language added':['globe','added as a language'],'language updated':['globe','language settings updated'],'language deleted':['trash','removed as a language']};
function activities(items) {
  if(!items.length) return `<div class="empty"><b>Nothing yet.</b><span>Edits and publications will show up here.</span></div>`;
  return items.map(item=>{ const [ic,verb]=ACTIVITY[item.action]||['activity',item.action]; const kind=item.action.split(' ').at(-1);
    return `<div class="activity"><span class="activity-icon ${esc(kind)}">${icon(ic)}</span><div><p><b>${esc(item.lesson_title)}</b> ${esc(verb)}</p><small>${esc(item.admin_email)} · ${relative(item.created_at)}</small></div></div>`; }).join('');
}
function lessonTable(rows, {empty='No lessons match.'}={}) {
  if(!rows.length) return `<div class="empty"><b>${empty}</b><span>Try another filter, or start a new unit.</span><button class="btn new-lesson">${icon('plus')}New lesson</button></div>`;
  return `<table class="table"><thead><tr><th>Lesson</th><th>Language</th><th>Status</th><th>Content</th><th>Updated</th></tr></thead><tbody>${rows.map(row=>`<tr data-edit="${esc(row.id)}"><td><button class="lesson-cell" data-edit="${esc(row.id)}">${lessonIcon(row.content)}<span><b>${esc(row.content.title)}</b><small>Unit ${row.content.position}${row.content.titleEn?' · '+esc(row.content.titleEn):''}</small></span></button></td><td class="indent">${langTag(row.content.language)}</td><td class="indent">${badge(row)}</td><td class="num hide-sm">${plural(row.content.words.length,'word')} · ${plural(row.content.sentences.length,'sentence')}</td><td class="num hide-sm">${date(row.updated_at)}</td></tr>`).join('')}</tbody></table>`;
}
function languagesTable() {
  if(!state.languages.length) return `<div class="empty"><b>No languages yet.</b><button class="btn new-language">${icon('plus')}Add language</button></div>`;
  return `<table class="table"><thead><tr><th>Language</th><th>Code</th><th>Greeting</th><th>Units</th><th>Learners see it</th></tr></thead><tbody>${state.languages.map(l=>{
    const lessons=state.lessons.filter(x=>x.content.language===l.code), live=lessons.filter(x=>x.published).length;
    const shown=l.published&&live>0;
    return `<tr data-edit-language="${esc(l.code)}"><td><span class="lesson-cell"><span class="lang-swatch" data-c="${esc(l.color)}">${esc(l.code)}</span><span><b>${esc(l.name_en)}</b><small>${esc(l.name_fr)}${l.region_en?' · '+esc(l.region_en):''}</small></span></span></td><td class="hide-sm"><span class="code">${esc(l.code)}</span></td><td class="hide-sm">${esc(l.greeting)||'<span class="muted">—</span>'}</td><td class="num indent">${live} published · ${lessons.length} total</td><td class="indent"><span class="badge ${shown?'visible':'hidden'}">${shown?'Visible':l.published?'Visible, no published units':'Hidden'}</span></td></tr>`;
  }).join('')}</tbody></table>`;
}
function render() {
  const all=state.lessons, live=all.filter(l=>l.published).length, review=all.filter(l=>status(l)!=='published').length;
  $('nav-count').textContent=all.length||''; $('nav-languages').textContent=state.languages.length||'';
  $('nav-pending').textContent=state.pending; $('nav-pending').hidden=!state.pending;
  const words=all.reduce((n,l)=>n+l.content.words.length,0);
  $('stats').innerHTML=[['Lessons',all.length,`${plural(state.languages.length,'language')}`],['Published',live,'Live in the learner app'],['Needs review',review,'Drafts and unpublished changes'],['Learners',state.learners,'See their progress','learners']]
    .map(([label,value,sub,view])=>`<div class="stat${view?' link':''}" ${view?`data-view="${view}" role="button" tabindex="0"`:''}><span>${label}</span><b>${value.toLocaleString('en')}</b><small>${sub}</small></div>`).join('');
  $('nav-learners').textContent=state.learners||'';
  $('language-summary').innerHTML=languageSummary();
  $('recent-activity').innerHTML=activities(state.activity.slice(0,5)); $('activity-list').innerHTML=activities(state.activity);
  $('recent-table').innerHTML=lessonTable([...all].sort((a,b)=>new Date(b.updated_at)-new Date(a.updated_at)).slice(0,5),{empty:'No lessons yet.'});
  const options=state.languages.map(l=>`<option value="${esc(l.code)}">${esc(l.name_en)}</option>`).join('');
  $('language-filter').innerHTML=`<option value="all">All languages</option>${options}`; $('language-filter').value=state.language;
  const query=state.search.toLowerCase().trim();
  const filtered=all.filter(row=>(state.language==='all'||row.content.language===state.language)
    && (state.status==='all'||state.status==='published'&&!!row.published||status(row)===state.status)
    && (!query||[row.content.title,row.content.titleEn,...row.content.words.flatMap(w=>[w.term,w.fr,w.en])].join(' ').toLowerCase().includes(query))).sort(byCurriculum);
  $('lessons-table').innerHTML=lessonTable(filtered);
  $('languages-table').innerHTML=languagesTable();
  renderLearners();
  applyVars();
}

// ---------- recordings ----------
let recorder=null, player=null;
async function uploadAudio(blob) {
  if(blob.size>1500000) throw new Error('Keep recordings under 1.5 MB.');
  const response=await fetch('/api/admin/audio',{method:'POST',credentials:'same-origin',headers:{'Content-Type':blob.type||'application/octet-stream'},body:blob});
  const data=await response.json().catch(()=>({})); if(!response.ok) throw new Error(data.error||'Upload failed.'); return data.id;
}
function audioControls(row) {
  const input=row.querySelector('[data-field="audio"]'), stateEl=row.querySelector('.audio .state'), msg=row.querySelector('.audio-msg');
  const [record,upload,play,clear]=['record','upload','play','clear'].map(n=>row.querySelector(`.a-${n}`)), file=row.querySelector('input[type=file]');
  const paint=(note='')=>{ const has=!!input.value; play.hidden=clear.hidden=!has; record.classList.toggle('has',has); stateEl.textContent=has?'Recorded':''; stateEl.classList.toggle('ok',has); msg.textContent=note; };
  const attach=async blob=>{ stateEl.textContent='Uploading…'; try{ input.value=await uploadAudio(blob); state.dirty=true; paint(); }catch(error){ paint(error.message); } };
  record.onclick=async()=>{
    if(recorder){ recorder.stop(); return; }
    if(!navigator.mediaDevices||!window.MediaRecorder){ paint('This browser can’t record. Upload a file instead.'); return; }
    try {
      const stream=await navigator.mediaDevices.getUserMedia({audio:true}); const chunks=[]; const active=recorder=new MediaRecorder(stream);
      active.ondataavailable=e=>chunks.push(e.data);
      active.onstop=()=>{ stream.getTracks().forEach(t=>t.stop()); recorder=null; record.classList.remove('recording'); record.innerHTML=icon('mic'); record.setAttribute('aria-label','Record'); attach(new Blob(chunks,{type:active.mimeType})); };
      active.start(); record.classList.add('recording'); record.innerHTML=icon('stop'); record.setAttribute('aria-label','Stop recording'); stateEl.textContent='Recording…';
      setTimeout(()=>{ if(recorder===active) active.stop(); },15000);
    } catch { paint('Microphone access was blocked. Allow it, or upload a file.'); }
  };
  upload.onclick=()=>file.click();
  file.onchange=()=>{ if(file.files[0]) attach(file.files[0]); file.value=''; };
  play.onclick=()=>{ player?.pause(); player=new Audio(`/api/v1/audio/${input.value}`); player.play().catch(()=>paint('This recording can’t be played here.')); };
  clear.onclick=()=>{ input.value=''; state.dirty=true; paint(); };
  paint();
}

// ---------- lesson editor ----------
function renumber() {
  [...$('word-rows').children].forEach((r,i)=>r.querySelector('.idx').textContent=i+1);
  [...$('sentence-rows').children].forEach((r,i)=>r.querySelector('.idx').textContent=i+1);
  const words=[...$('word-rows').children].filter(r=>r.querySelector('[data-field="term"]').value.trim()).length;
  $('word-count').textContent=words; $('word-count').classList.toggle('warn',words<4);
  $('sentence-count').textContent=$('sentence-rows').children.length;
}
function switchTab(name) {
  document.querySelectorAll('.sheet-tabs [data-tab]').forEach(b=>b.setAttribute('aria-selected',String(b.dataset.tab===name)));
  document.querySelectorAll('#lesson-form [data-panel]').forEach(p=>p.hidden=p.dataset.panel!==name);
  $('editor').querySelector('.sheet-body').scrollTop=0;
}
// Keeps the "how learners see it" card and the position hint in step with the form.
function updatePreview() {
  const form=$('lesson-form'), f=new FormData(form), l=lang(f.get('language')||'');
  const position=Number(f.get('position'))||0;
  $('preview-icon').innerHTML=lessonIcon({icon:f.get('icon')}); $('preview-icon').dataset.c=l.color; applyVars($('preview-icon').parentElement);
  $('preview-meta').textContent=`${l.name_fr} · Unité ${position||'?'}`;
  $('preview-title').textContent=f.get('title')||'Titre de l’unité';
  $('preview-sub').textContent=f.get('titleEn')||'';
  const others=state.lessons.filter(x=>x.content.language===l.code && x.id!==state.editing?.id);
  const clash=others.find(x=>x.content.position===position);
  const hint=$('position-hint');
  hint.textContent=clash?`Unit ${position} is already “${clash.content.title}”. Pick another number, or both will share the spot.`:`${l.name_en} has ${plural(others.length+(state.editing?.content.language===l.code?1:0),'unit')}. Learners unlock them in this order.`;
  hint.classList.toggle('warn',!!clash);
}
function renderPickers(content) {
  const langs=state.languages.some(l=>l.code===content.language)?state.languages:[...state.languages,lang(content.language)];
  $('editor-language').innerHTML=langs.map(l=>`<label class="chip" data-c="${esc(l.color)}"><input type="radio" name="language" value="${esc(l.code)}" ${l.code===content.language?'checked':''}><span>${esc(l.name_en)}</span></label>`).join('');
  const topics=TOPICS.some(([e])=>e===content.icon)?TOPICS:[...TOPICS,[content.icon,'Current']];
  $('icon-picker').innerHTML=topics.map(([emoji,label])=>`<label title="${esc(label)}"><input type="radio" name="icon" value="${esc(emoji)}" ${emoji===content.icon?'checked':''} aria-label="${esc(label)}">${lessonIcon({icon:emoji})}<small>${esc(label)}</small></label>`).join('');
  applyVars($('editor-language'));
}
function editorChrome(row, content) {
  const l=lang(content.language);
  $('editor-heading').textContent=row?content.title||'Untitled lesson':'New lesson';
  $('editor-eyebrow').textContent=row?`${l.name_en} · Unit ${content.position}`:'Draft';
  $('editor-status').innerHTML=row?badge(row):'';
  const notice=$('editor-notice'), s=row?status(row):'draft';
  notice.className='banner'+(s==='published'?' live':s==='changes'?' changes':'');
  notice.innerHTML=icon('info')+`<span>${s==='published'?'<b>Live for learners.</b> Edits you save stay private until you press Publish.':s==='changes'?'<b>Live, with saved changes.</b> Learners still see the published version until you press Publish.':'<b>Draft.</b> Only your team can see it. Press Publish when it’s ready.'}</span>`;
  $('delete-lesson').hidden=!row; $('unpublish-lesson').hidden=!row?.published;
}
function openEditor(id) {
  if(!state.languages.length){ toast('Add a language first.'); openLanguage(); return; }
  const row=state.lessons.find(l=>l.id===id); state.editing=row||null; state.dirty=false;
  const language=state.language!=='all'?state.language:state.languages[0].code;
  const content=row?.content||{language,position:Math.max(0,...state.lessons.filter(l=>l.content.language===language).map(l=>l.content.position))+1,title:'',titleEn:'',icon:'📚',tip:'',words:[],sentences:[]};
  renderPickers(content);
  for(const name of ['position','title','titleEn','tip']) $('lesson-form').elements.namedItem(name).value=content[name];
  $('word-rows').replaceChildren(); content.words.forEach(w=>wordRow(w));
  $('sentence-rows').replaceChildren(); content.sentences.forEach(s=>sentenceRow(s));
  if(!content.words.length) wordRow();
  renumber(); editorChrome(row,content); updatePreview(); switchTab('details');
  $('editor-error').textContent=''; $('editor').showModal();
  state.dirty=false;
}
function wordRow(word={term:'',fr:'',en:''}) {
  const row=document.createElement('div'); row.className='word-row vocab-grid';
  row.innerHTML=`<span class="idx"></span><input class="term" data-field="term" value="${esc(word.term)}" maxlength="300" required placeholder="Mbote" aria-label="Word or phrase"><input class="fr" data-field="fr" value="${esc(word.fr)}" maxlength="300" required placeholder="Bonjour" aria-label="French meaning"><input class="en" data-field="en" value="${esc(word.en)}" maxlength="300" placeholder="Hello" aria-label="English meaning"><span class="audio"><input type="hidden" data-field="audio" value="${esc(word.audio||'')}"><button type="button" class="icon-btn sm a-record" aria-label="Record">${icon('mic')}</button><button type="button" class="icon-btn sm a-upload" aria-label="Upload audio">${icon('upload')}</button><button type="button" class="icon-btn sm a-play" aria-label="Listen" hidden>${icon('play')}</button><button type="button" class="icon-btn sm a-clear danger" aria-label="Remove recording" hidden>${icon('x')}</button><input type="file" accept="audio/*" hidden><span class="state"></span></span><button type="button" class="icon-btn sm danger remove" aria-label="Remove word">${icon('trash')}</button><p class="audio-msg"></p>`;
  $('word-rows').append(row); audioControls(row); renumber(); return row;
}
function sentenceRow(sentence={fr:'',en:'',answer:'',distractors:[]}) {
  const row=document.createElement('div'); row.className='sentence-row';
  row.innerHTML=`<span class="idx"></span><div class="sentence-fields"><label class="field">Prompt in French<input data-field="fr" value="${esc(sentence.fr)}" required maxlength="600" placeholder="Je vais bien"></label><label class="field">Prompt in English<input data-field="en" value="${esc(sentence.en)}" maxlength="600" placeholder="I’m fine"></label><label class="field">Correct answer<input data-field="answer" value="${esc(sentence.answer)}" required maxlength="600" placeholder="Nazali malamu"></label><label class="field">Distractors<input data-field="distractors" value="${esc(sentence.distractors.join(', '))}" placeholder="Ozali, yo"><small>Separate with commas.</small></label></div><button type="button" class="icon-btn sm danger remove" aria-label="Remove sentence">${icon('trash')}</button>`;
  $('sentence-rows').append(row); renumber(); return row;
}
function readEditor() {
  const fields=new FormData($('lesson-form'));
  const pick=row=>Object.fromEntries([...row.querySelectorAll('[data-field]')].map(el=>[el.dataset.field,el.value.trim()]));
  const words=[...$('word-rows').children].map(pick).filter(w=>w.term||w.fr||w.en||w.audio).map(w=>{ if(!w.audio) delete w.audio; return w; });
  return {language:fields.get('language'),position:Number(fields.get('position')),icon:fields.get('icon'),title:fields.get('title'),titleEn:fields.get('titleEn'),tip:fields.get('tip'),
    words,sentences:[...$('sentence-rows').children].map(row=>{ const s=pick(row); s.distractors=s.distractors.split(',').map(t=>t.trim()).filter(Boolean); return s; })};
}
function validEditor() {
  // Empty trailing word rows are ignored; everything else must be complete.
  for(const row of $('word-rows').children) { const inputs=[...row.querySelectorAll('input[data-field="term"],input[data-field="fr"]')]; const used=[...row.querySelectorAll('input[data-field]')].some(i=>i.value.trim()); inputs.forEach(i=>i.required=used); }
  // Fields on another tab can't show their message, so open the tab holding the first problem.
  const invalid=$('lesson-form').querySelector('input:invalid,textarea:invalid');
  if(invalid) switchTab(invalid.closest('[data-panel]').dataset.panel);
  return $('lesson-form').reportValidity();
}
function busy(value) { state.busy=value; $('lesson-form').querySelectorAll('button,input,select,textarea').forEach(el=>el.disabled=value); }
function savedRow(row) {
  state.editing=row; state.dirty=false;
  const i=state.lessons.findIndex(l=>l.id===row.id); if(i<0) state.lessons.push(row); else state.lessons[i]=row;
  editorChrome(row,row.content);
}
async function save(publish=false) {
  if(state.busy || !validEditor()) return;
  const data=readEditor();
  if(publish&&!data.words.length) { $('editor-error').textContent='Add at least one word before publishing.'; return; }
  busy(true); $('editor-error').textContent='';
  try {
    let row=state.editing;
    if(!row||state.dirty) {
      const result=await api(row?`/api/admin/lessons/${row.id}`:'/api/admin/lessons',{method:row?'PUT':'POST',body:JSON.stringify({...data,version:row?.version})});
      row=result.lesson; savedRow(row);
    }
    if(publish) { const result=await api(`/api/admin/lessons/${row.id}/publish`,{method:'POST',body:JSON.stringify({version:row.version})}); savedRow(result.lesson); }
    $('editor').close(); toast(publish?'Published. Learners will see it on their next visit.':'Draft saved.'); await refresh();
  } catch(error) { $('editor-error').textContent=error.message; }
  finally { busy(false); }
}
async function mutateLesson(action) {
  if(state.busy || !state.editing) return;
  const row=state.editing;
  const message=action==='delete'?`Delete “${row.content.title}”? This can’t be undone.`:`Unpublish “${row.content.title}”? Learners will stop seeing it.`;
  if(!confirm(message+(state.dirty?' Unsaved edits will be lost.':''))) return;
  busy(true); $('editor-error').textContent='';
  try {
    await api(`/api/admin/lessons/${row.id}${action==='unpublish'?'/unpublish':''}`,{method:action==='delete'?'DELETE':'POST',body:JSON.stringify({version:row.version})});
    state.dirty=false; $('editor').close(); toast(action==='delete'?'Lesson deleted.':'Lesson moved back to draft.'); await refresh();
  } catch(error) { $('editor-error').textContent=error.message; } finally { busy(false); }
}
function closeEditor() { if(state.busy) return; if(state.dirty&&!confirm('Discard your unsaved changes?')) return; state.dirty=false; $('editor').close(); }
function preview() {
  const c=readEditor(), l=lang(c.language);
  $('preview-content').innerHTML=`<div class="preview-title"><span class="big">${esc(c.icon)}</span><p class="eyebrow">${esc(l.name_en)} · Unit ${c.position}</p><h2>${esc(c.title)||'Untitled lesson'}</h2>${c.titleEn?`<p class="muted">${esc(c.titleEn)}</p>`:''}</div>${c.tip?`<p class="preview-note">${esc(c.tip)}</p>`:''}<h3>Words</h3>${c.words.length?`<div class="preview-list">${c.words.map(w=>`<div><b>${esc(w.term)}</b><span>${esc(w.fr)}${w.en?' · '+esc(w.en):''}</span></div>`).join('')}</div>`:'<p class="muted">No words yet.</p>'}${c.sentences.length?`<h3>Sentences</h3><div class="preview-list">${c.sentences.map(s=>`<div><b>${esc(s.fr)}</b><span>${esc(s.answer)}</span></div>`).join('')}</div>`:''}`;
  $('preview').showModal();
}

// ---------- languages ----------
function openLanguage(code) {
  const l=code?state.languages.find(x=>x.code===code):null; state.editingLanguage=l||null;
  const form=$('language-form'); form.reset();
  const value=l||{name_fr:'',name_en:'',code:'',greeting:'',cheer:'',region_fr:'',region_en:'',color:SWATCHES.find(c=>!state.languages.some(x=>x.color===c))||SWATCHES[0],position:Math.max(0,...state.languages.map(x=>x.position))+1,published:false};
  for(const name of ['name_fr','name_en','code','greeting','cheer','region_fr','region_en','position']) form.elements.namedItem(name).value=value[name];
  form.elements.namedItem('code').disabled=!!l; form.elements.namedItem('published').checked=value.published;
  const colors=SWATCHES.includes(value.color)?SWATCHES:[...SWATCHES,value.color];
  $('swatches').innerHTML=colors.map(c=>`<label title="${c}"><input type="radio" name="color" value="${c}" ${c===value.color?'checked':''} aria-label="Colour ${c}"><span data-c="${c}"></span></label>`).join('');
  applyVars($('swatches'));
  const used=l&&state.lessons.some(x=>x.content.language===l.code);
  $('delete-language').hidden=!l||used;
  $('language-heading').textContent=l?`Edit ${l.name_en}`:'Add a language';
  $('language-error').textContent=''; $('language-dialog').showModal();
}
async function saveLanguage(event) {
  event.preventDefault();
  const form=$('language-form'); if(!form.reportValidity()) return;
  const f=new FormData(form);
  const body={name_fr:f.get('name_fr'),name_en:f.get('name_en'),code:(f.get('code')||'').toLowerCase(),greeting:f.get('greeting'),cheer:f.get('cheer'),region_fr:f.get('region_fr'),region_en:f.get('region_en'),color:f.get('color'),position:Number(f.get('position')),published:form.elements.namedItem('published').checked};
  const editing=state.editingLanguage;
  try {
    await api(editing?`/api/admin/languages/${editing.code}`:'/api/admin/languages',{method:editing?'PUT':'POST',body:JSON.stringify(body)});
    $('language-dialog').close(); toast(editing?'Language saved.':`${body.name_en} added. Create its first lesson when you’re ready.`); await refresh();
  } catch(error) { $('language-error').textContent=error.message; }
}
async function deleteLanguage() {
  const l=state.editingLanguage; if(!l||!confirm(`Delete ${l.name_en}? This can’t be undone.`)) return;
  try { await api(`/api/admin/languages/${l.code}`,{method:'DELETE',body:'{}'}); $('language-dialog').close(); toast('Language deleted.'); await refresh(); }
  catch(error) { $('language-error').textContent=error.message; }
}

// ---------- learners ----------
const DAY=86400000;
const daysAgo = day => day?Math.floor((Date.now()-Date.parse(day+'T12:00:00Z'))/DAY):null;
function lastSeen(l) {
  const d=daysAgo(l.lastActiveDay);
  if(d===null) return l.lessonsDone?'—':'Not started';
  return d<=0?'Today':d===1?'Yesterday':d<7?`${d} days ago`:date(l.lastActiveDay);
}
async function loadLearners() {
  $('learners-error').textContent='';
  if(!state.learnerRows) $('learners-table').innerHTML='<div class="empty">Loading learners…</div>';
  try { state.learnerRows=(await api('/api/admin/learners')).learners; state.learners=state.learnerRows.length; render(); }
  catch(error) { $('learners-error').textContent=error.message; }
}
function unitsDone(l) { return Object.values(l.progress).filter(n=>n>=4).length; }
function renderLearners() {
  const rows=state.learnerRows; if(!rows) return;
  const active=rows.filter(l=>{ const d=daysAgo(l.lastActiveDay); return d!==null&&d<7; }).length;
  $('learner-stats').innerHTML=[['Learners',rows.length,'Accounts created'],['Active this week',active,'Practised in the last 7 days'],['Lessons completed',rows.reduce((n,l)=>n+l.lessonsDone,0),'By all learners'],['Words learned',rows.reduce((n,l)=>n+l.wordsLearned,0),'Across all languages']]
    .map(([label,value,sub])=>`<div class="stat"><span>${label}</span><b>${value.toLocaleString('en')}</b><small>${sub}</small></div>`).join('');
  $('learner-language').innerHTML=`<option value="all">All languages</option>${state.languages.map(l=>`<option value="${esc(l.code)}">${esc(l.name_en)}</option>`).join('')}`;
  $('learner-language').value=state.learnerLanguage;
  const q=state.learnerSearch.toLowerCase().trim();
  const sorters={recent:(a,b)=>(b.lastActiveDay||'').localeCompare(a.lastActiveDay||'')||new Date(b.joined)-new Date(a.joined),xp:(a,b)=>b.xp-a.xp,streak:(a,b)=>b.streak-a.streak||b.bestStreak-a.bestStreak,new:(a,b)=>new Date(b.joined)-new Date(a.joined)};
  const list=rows.filter(l=>(state.learnerLanguage==='all'||l.language===state.learnerLanguage)&&(!q||`${l.name} ${l.email}`.toLowerCase().includes(q))).sort(sorters[state.learnerSort]);
  if(!rows.length) { $('learners-table').innerHTML='<div class="empty"><b>No learners yet.</b><span>People who create an account in the learner app appear here.</span></div>'; return; }
  if(!list.length) { $('learners-table').innerHTML='<div class="empty"><b>No learners match.</b><span>Try another search or language.</span></div>'; return; }
  $('learners-table').innerHTML=`<table class="table"><thead><tr><th>Learner</th><th>Learning</th><th>XP</th><th>Streak</th><th>Lessons</th><th>Words</th><th>Last practice</th><th>Joined</th></tr></thead><tbody>${list.map(l=>`<tr data-learner="${esc(l.id)}"><td><span class="lesson-cell"><span class="me-avatar">${esc(l.name[0]?.toUpperCase()||'?')}</span><span><b>${esc(l.name)}</b><small>${esc(l.email)}</small></span></span></td><td class="indent">${l.language?langTag(l.language):'<span class="muted">Not chosen</span>'}</td><td class="num indent">${l.xp.toLocaleString('en')} XP</td><td class="num hide-sm">${l.streak?`<span class="streak">${icon('flame')}${l.streak}</span>`:'<span class="muted">0</span>'}</td><td class="num hide-sm">${l.lessonsDone}</td><td class="num hide-sm">${l.wordsLearned}</td><td class="num indent">${lastSeen(l)}</td><td class="num hide-sm">${date(l.joined)}</td></tr>`).join('')}</tbody></table>`;
  applyVars($('learners-table'));
}
function openLearner(id) {
  const l=state.learnerRows?.find(x=>x.id===id); if(!l) return;
  $('learner-avatar').textContent=l.name[0]?.toUpperCase()||'?'; $('learner-heading').textContent=l.name; $('learner-email').textContent=l.email;
  const tiles=[['XP',l.xp.toLocaleString('en')],['Current streak',`${l.streak} ${l.streak===1?'day':'days'}`],['Best streak',`${l.bestStreak} ${l.bestStreak===1?'day':'days'}`],['Lessons done',l.lessonsDone],['Words learned',l.wordsLearned],['Daily goal',l.goal?`${l.goal} XP`:'—']];
  // Units grouped by language, in course order, for every language the learner has touched.
  const codes=[...new Set([l.language,...state.lessons.filter(x=>l.progress[x.id]).map(x=>x.content.language)].filter(Boolean))];
  const courses=codes.map(code=>{
    const units=state.lessons.filter(x=>x.content.language===code&&x.published).sort(byCurriculum);
    const done=units.filter(u=>(l.progress[u.id]||0)>=4).length;
    return `<section class="course"><header><span class="lang-tag" data-c="${esc(lang(code).color)}">${esc(lang(code).name_en)}</span><small>${done} of ${units.length} units complete${code===l.language?' · learning now':''}</small></header>
      ${units.map(u=>{ const n=l.progress[u.id]||0; return `<div class="course-unit ${n>=4?'done':n?'started':''}">${lessonIcon(u.content)}<span><b>${esc(u.content.title)}</b><small>Unit ${u.content.position} · ${n>=4?'Complete':n?`${n} of 4 lessons`:'Not started'}</small></span><span class="meter" data-c="${esc(lang(code).color)}"><i data-pct="${n*25}"></i></span></div>`; }).join('')}</section>`;
  }).join('');
  $('learner-body').innerHTML=`<div class="tiles">${tiles.map(([k,v])=>`<div><span>${k}</span><b>${v}</b></div>`).join('')}</div>
    <p class="muted small">Joined ${date(l.joined)} · last practice ${lastSeen(l).toLowerCase()}${l.lastSync?` · progress saved ${relative(l.lastSync).toLowerCase()}`:''}</p>
    ${courses||'<div class="empty"><b>No lessons started yet.</b><span>Their progress shows here once they finish a lesson.</span></div>'}`;
  applyVars($('learner-body'));
  $('learner-dialog').showModal();
}

// ---------- team ----------
async function refreshAccounts(quiet=false) {
  if(!quiet) { $('accounts-error').textContent=''; $('accounts-list').innerHTML='<div class="empty">Loading…</div>'; }
  try {
    const {accounts}=await api('/api/admin/accounts'); state.pending=accounts.length; render();
    $('accounts-list').innerHTML=accounts.length?accounts.map(a=>`<div class="request"><div><b>${esc(a.email)}</b><small>Requested ${date(a.created_at)}</small></div><button class="btn" data-approve="${esc(a.id)}" data-email="${esc(a.email)}">${icon('check')}Approve</button></div>`).join(''):'<div class="empty"><b>No pending requests.</b><span>New requests from /signup appear here.</span></div>';
  } catch(error) { if(!quiet){ $('accounts-list').textContent=''; $('accounts-error').textContent=error.message; } }
}
$('refresh-accounts').onclick=()=>refreshAccounts();
$('accounts-list').onclick=async event=>{
  const button=event.target.closest('[data-approve]'); if(!button) return;
  if(!confirm(`Give ${button.dataset.email} access to edit and publish all lessons?`)) return;
  button.disabled=true;
  try { await api(`/api/admin/accounts/${button.dataset.approve}/approve`,{method:'POST',body:'{}'}); toast('Approved. They can sign in now.'); await refreshAccounts(); await refresh(); }
  catch(error) { $('accounts-error').textContent=error.message; button.disabled=false; }
};

// ---------- auth ----------
$('signup-form').onsubmit=async event=>{
  event.preventDefault(); const form=event.currentTarget, button=form.querySelector('button[type=submit]');
  const fields=new FormData(form); $('signup-error').textContent='';
  if(fields.get('password')!==fields.get('confirmation')) { $('signup-error').textContent='The passwords don’t match.'; return; }
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
  try { const {user}=await api('/api/auth/login',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(event.currentTarget)))}); $('login-form').reset(); await start(user); }
  catch(error){ $('login-error').textContent=error.message; } finally { button.disabled=false; }
});
$('logout').onclick=async()=>{ try{ await api('/api/auth/logout',{method:'POST',body:'{}'}); Object.assign(state,{lessons:[],activity:[],languages:[]}); showLogin(); }catch(error){ toast(error.message); } };

// ---------- events ----------
document.addEventListener('click',event=>{
  const target=event.target.closest('button,tr,[data-view]'); if(!target) return;
  const d=target.dataset;
  if(d.view) navigate(d.view);
  if(target.classList.contains('new-lesson')) openEditor();
  if(target.classList.contains('new-language')) openLanguage();
  if(d.edit) openEditor(d.edit);
  if(d.editLanguage) openLanguage(d.editLanguage);
  if(d.learner) openLearner(d.learner);
  if(d.tab) switchTab(d.tab);
  if(d.filterLanguage) { state.language=d.filterLanguage; state.status='all'; document.querySelectorAll('[data-status]').forEach(b=>b.classList.toggle('selected',b.dataset.status==='all')); navigate('lessons'); }
  if(d.status) { state.status=d.status; document.querySelectorAll('[data-status]').forEach(b=>b.classList.toggle('selected',b===target)); render(); }
  if(target.classList.contains('remove')) { target.closest('.word-row,.sentence-row').remove(); state.dirty=true; renumber(); }
});
$('primary-action').onclick=()=>VIEWS[state.view][2]==='language'?openLanguage():openEditor();
$('search').oninput=e=>{ state.search=e.target.value; render(); };
$('language-filter').onchange=e=>{ state.language=e.target.value; render(); };
$('retry').onclick=refresh;
$('add-word').onclick=()=>{ wordRow().querySelector('input').focus(); state.dirty=true; };
$('add-sentence').onclick=()=>{ sentenceRow().querySelector('input').focus(); state.dirty=true; };
$('lesson-form').oninput=e=>{ state.dirty=true; if(e.target.dataset.field==='term') renumber(); else updatePreview(); };
$('lesson-form').onchange=e=>{ if(e.target.type==='radio') { state.dirty=true; updatePreview(); } };
$('learner-search').oninput=e=>{ state.learnerSearch=e.target.value; renderLearners(); };
$('learner-language').onchange=e=>{ state.learnerLanguage=e.target.value; renderLearners(); };
$('learner-sort').onchange=e=>{ state.learnerSort=e.target.value; renderLearners(); };
$('close-learner').onclick=()=>$('learner-dialog').close();
document.addEventListener('keydown',e=>{ if(e.key==='Enter'&&e.target.matches?.('.stat.link')) e.target.click(); });
$('lesson-form').onsubmit=e=>{ e.preventDefault(); save(); };
$('publish-lesson').onclick=()=>save(true); $('delete-lesson').onclick=()=>mutateLesson('delete'); $('unpublish-lesson').onclick=()=>mutateLesson('unpublish');
$('close-editor').onclick=closeEditor; $('editor').addEventListener('cancel',e=>{ e.preventDefault(); closeEditor(); });
$('preview-lesson').onclick=preview; $('close-preview').onclick=()=>$('preview').close();
$('language-form').onsubmit=saveLanguage; $('delete-language').onclick=deleteLanguage;
$('close-language').onclick=$('cancel-language').onclick=()=>$('language-dialog').close();
window.addEventListener('beforeunload',e=>{ if(state.dirty){ e.preventDefault(); e.returnValue=''; } });

async function start(user) { showWorkspace(user); navigate(state.view); await refresh(); refreshAccounts(true); }
(async()=>{
  if(location.pathname.replace(/\/$/,'')==='/signup') { showLogin(); return; }
  try { const {user}=await api('/api/auth/me'); await start(user); }
  catch(error) { showLogin(); if(error.message!=='Sign in to continue.') $('login-error').textContent=error.message; }
})();
