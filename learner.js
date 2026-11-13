/* Loba's presentation layer. The existing lesson engine and progress key stay intact. */
(() => {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const animations = new Map();
  const icon = (name) => {
    const paths = {
      home:'<path d="m3 11 9-8 9 8v9a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
      chat:'<path d="M21 11a8 8 0 0 1-8 8H7l-5 3 2-6a8 8 0 0 1-1-5 9 9 0 0 1 18 0Z"/><path d="M8 10h8M8 14h5"/>',
      cup:'<path d="M7 3h10v6a5 5 0 0 1-10 0zM7 5H3v3a4 4 0 0 0 4 4m10-7h4v3a4 4 0 0 1-4 4M12 14v6m-5 1h10"/>',
      target:'<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
      user:'<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>'
    };
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]}</svg>`;
  };
  document.querySelectorAll('#nav button').forEach((b,i) => {
    b.querySelector('span').innerHTML=icon(['home','chat','cup','target','user'][i]);
  });
  const brand=document.createElement('div');brand.className='rail-brand';
  brand.innerHTML='<div class="logo"><div class="flag"></div>loba<span>✦</span></div><p>Un mot. Un lien.</p>';
  $('nav').prepend(brand);
  const footer=document.createElement('div');footer.className='rail-footer';
  footer.innerHTML='<div class="flag-stripe"></div><b>CONGO, DANS LA VOIX.</b><small>Lingala & Swahili</small>';
  $('nav').append(footer);

  function mount(el, file, loop=true) {
    if(!window.lottie || animations.has(el))return;
    el.classList.remove('animation-loaded');
    const fallback=el.innerHTML;
    const canvas=document.createElement('div');canvas.className='animation-canvas';el.append(canvas);
    const anim=lottie.loadAnimation({container:canvas,renderer:'svg',loop,autoplay:false,path:file});
    const record={anim,visible:false,loop};animations.set(el,record);
    anim.addEventListener('DOMLoaded',()=>{el.classList.add('animation-loaded');if(reduced.matches)anim.goToAndStop(loop?0:Math.min(90,anim.totalFrames-1),true);else if(record.visible&&!document.hidden)anim.play()});
    anim.addEventListener('data_failed',()=>{anim.destroy();animations.delete(el);el.classList.remove('animation-loaded');el.innerHTML=fallback});
    observer.observe(el);
  }
  const observer=new IntersectionObserver(entries=>entries.forEach(e=>{
    const r=animations.get(e.target);if(!r)return;r.visible=e.isIntersecting;
    if(!reduced.matches&&!document.hidden&&r.visible)r.anim.play();else r.anim.pause();
  }),{threshold:.1});
  function syncMotion(){for(const r of animations.values()){
    if(reduced.matches)r.anim.goToAndStop(r.loop?0:Math.min(90,r.anim.totalFrames-1),true);
    else if(r.visible&&!document.hidden)r.anim.play();else r.anim.pause();
  }}
  reduced.addEventListener('change',syncMotion);document.addEventListener('visibilitychange',syncMotion);
  function cleanup(){for(const [el,r] of animations){if(!el.isConnected){observer.unobserve(el);r.anim.destroy();animations.delete(el)}}}
  const say=(fr,en)=>S.ui==='en'?en:fr;
  function updateSound(){const b=$('soundToggle');b.textContent=S.sound===false?'♪̸':'♫';b.setAttribute('aria-pressed',String(S.sound!==false));b.title=say('Effets sonores','Sound effects');b.setAttribute('aria-label',b.title);}
  // Keep preferences alongside the existing local/cloud progress without changing its schema.
  $('soundToggle').onclick=()=>{S.sound=S.sound===false;save();updateSound();if(S.sound)sfx('pop')};
  function enhance(){
    cleanup();updateSound();
    document.querySelectorAll('#nav button').forEach(b=>{if(b.classList.contains('on'))b.setAttribute('aria-current','page');else b.removeAttribute('aria-current')});
    document.querySelectorAll('.mascot').forEach(el=>{if(!el.querySelector('.animation-canvas')){el.innerHTML='<span class="animation-fallback">🦜</span>';mount(el,'lottie-parrot.json')}});
    if(!$('sLearn').classList.contains('on'))return;
    const current=Math.max(0,LG.units.findIndex(u=>(LP().prog[u.id]||0)<LPU));
    const done=LG.units.reduce((sum,u)=>sum+Math.min(LPU,LP().prog[u.id]||0),0);
    const hero=$('journeyHero');
    if(!hero.children.length){hero.innerHTML=`<div class="journey-copy"><div class="eyebrow">✦ <span id="heroEyebrow"></span></div><h1 id="heroTitle"></h1><p id="heroText"></p><button class="btn" id="resumeJourney"></button></div><div class="journey-scene" aria-hidden="true"><div class="scene-sun"></div><div class="scene-cloud c1"></div><div class="scene-cloud c2"></div><div class="scene-hill back"></div><div class="scene-hill front"></div><div class="scene-river"></div><div class="hero-parrot"><span class="animation-fallback">🦜</span></div><span class="scene-star">✦</span><span class="scene-word"></span></div>`;mount(hero.querySelector('.hero-parrot'),'lottie-parrot.json')}
    $('heroEyebrow').textContent=say('LES LANGUES DU CONGO','THE LANGUAGES OF CONGO');
    $('heroTitle').textContent=LG.hi.replace(/[!\s]+$/,'')+' !';
    $('heroText').textContent=say('Un peu chaque jour. Et bientôt, toute une conversation.','A little every day. Soon, a whole conversation.');
    hero.querySelector('.scene-word').textContent=LANG_NAMES.en[S.lang];
    $('resumeJourney').textContent=say(done?'Continuer mon parcours':'C’est parti !',done?'Continue my journey':'Let’s begin!')+' →';
    $('resumeJourney').onclick=()=>startLesson(current);
    $('mobilePractice').textContent=say('🧩 Pratiquer','🧩 Practice');$('mobilePractice').onclick=startMach;
    $('mobileDaily').textContent=say('☀ Mon quotidien','☀ My daily practice');$('mobileDaily').onclick=()=>{const open=$('sLearn').classList.toggle('daily-open');$('mobileDaily').setAttribute('aria-expanded',String(open))};
    $('journeyTitle').textContent=say('Ton parcours','Your journey');
    $('journeyCount').textContent=`${done} / ${LG.units.length*LPU} ${say('leçons','lessons')}`;
    const xp=S.q.xp||0,goal=S.goal||20,percent=Math.min(100,Math.round(xp/goal*100));
    $('dailyGoal').innerHTML=`<div class="goal-heading"><span class="goal-icon">☀</span><div><h3>${say('Un petit pas par jour','A little every day')}</h3><p>${say('Garde le rythme !','Keep your rhythm!')}</p></div></div><div class="goal-meter"><span style="width:${percent}%"></span></div><div class="goal-meta"><b>${xp} / ${goal} XP</b><span>${say('aujourd’hui','today')}</span></div>`;
    document.querySelectorAll('#path .node').forEach((b,i)=>{const unit=LG.units[Math.floor(i/(LPU+1))];b.setAttribute('aria-label',`${tr(unit.title)} · ${i%(LPU+1)===LPU?say('Trésor','Treasure'):t('lessonN',{n:i%(LPU+1)+1})}${b.classList.contains('locked')?' · '+say('verrouillé','locked'):''}`)});
    document.querySelectorAll('#path .nodes').forEach((n,i)=>{if(!n.querySelector('.path-marker')){const deco=document.createElement('div');deco.className='path-marker';deco.setAttribute('aria-hidden','true');deco.textContent=['🌴','🪘','🌿','☀️'][i%4];n.append(deco)}});
  }
  const originalRender=render;render=function(){originalRender();enhance()};
  const originalExercise=renderEx;renderEx=function(){originalExercise();enhance()};
  const originalOnboarding=renderOnb;renderOnb=function(){originalOnboarding();enhance()};
  document.addEventListener('loba:screen',enhance);
  document.addEventListener('loba:celebrate',()=>{const el=$('eIcon');for(const [node,r] of animations)if(node===el){observer.unobserve(node);r.anim.destroy();animations.delete(node)}el.innerHTML='<span class="animation-fallback">🎉</span>';mount(el,'lottie-celebrate.json',false)});
  enhance();
})();
