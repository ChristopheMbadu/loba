import type { LangCode, UiLang, Unit } from '../content/types'

/** Words come from the real first lessons in data/lessons.json. */
export type Taste = LangCode

export type Q =
  | { kind: 'meaning'; prompt: string; options: string[]; answer: number }
  | { kind: 'say'; prompt: string; options: string[]; answer: number }
  | { kind: 'pairs'; pairs: [term: string, meaning: string][] }

/** Hand-picked for the first two languages; others get a quiz built from their first unit. */
export const QUIZ: Record<string, Record<UiLang, Q[]>> = {
  ln: {
    fr: [
      { kind: 'meaning', prompt: 'Mbote', options: ['Merci', 'Bonjour', 'Au revoir'], answer: 1 },
      { kind: 'say', prompt: 'Merci', options: ['Malamu', 'Mbote', 'Matondo'], answer: 2 },
      { kind: 'pairs', pairs: [['Mama', 'Mère'], ['Tata', 'Père'], ['Ndeko', 'Frère / sœur']] },
    ],
    en: [
      { kind: 'meaning', prompt: 'Mbote', options: ['Thank you', 'Hello', 'Goodbye'], answer: 1 },
      { kind: 'say', prompt: 'Thank you', options: ['Malamu', 'Mbote', 'Matondo'], answer: 2 },
      { kind: 'pairs', pairs: [['Mama', 'Mother'], ['Tata', 'Father'], ['Ndeko', 'Brother / sister']] },
    ],
  },
  sw: {
    fr: [
      { kind: 'meaning', prompt: 'Jambo', options: ['Merci', 'Bonjour', 'Au revoir'], answer: 1 },
      { kind: 'say', prompt: 'Merci', options: ['Karibu', 'Asante', 'Nzuri'], answer: 1 },
      { kind: 'pairs', pairs: [['Mama', 'Mère'], ['Baba', 'Père'], ['Rafiki', 'Ami']] },
    ],
    en: [
      { kind: 'meaning', prompt: 'Jambo', options: ['Thank you', 'Hello', 'Goodbye'], answer: 1 },
      { kind: 'say', prompt: 'Thank you', options: ['Karibu', 'Asante', 'Nzuri'], answer: 1 },
      { kind: 'pairs', pairs: [['Mama', 'Mother'], ['Baba', 'Father'], ['Rafiki', 'Friend']] },
    ],
  },
}

const rotate = <T,>(list: T[], n: number) => list.map((_, i) => list[(i + n) % list.length]!)

/** Three questions from a unit's words: what a word means, how to say a word, and matching pairs. */
export function quizFromUnit(unit: Unit, ui: UiLang): Q[] {
  const meaning = (i: number) => { const w = unit.words[i % unit.words.length]!; return ui === 'en' && w.en ? w.en : w.fr }
  const term = (i: number) => unit.words[i % unit.words.length]!.term
  const pairIdx = unit.words.length >= 7 ? [4, 5, 6] : [1, 2, 3]
  return [
    { kind: 'meaning', prompt: term(0), options: rotate([meaning(0), meaning(1), meaning(2)], 1), answer: 2 },
    { kind: 'say', prompt: meaning(3), options: rotate([term(3), term(0), term(1)], 2), answer: 1 },
    { kind: 'pairs', pairs: pairIdx.map((i) => [term(i), meaning(i)] as [string, string]) },
  ]
}

export type Greeting = { text: string; lang: Taste; fr: string; en: string }

/** Greetings Loba cycles through when tapped. */
export const GREETINGS: Greeting[] = [
  { text: 'Mbote !', lang: 'ln', fr: 'Bonjour', en: 'Hello' },
  { text: 'Jambo !', lang: 'sw', fr: 'Bonjour', en: 'Hello' },
  { text: 'Sango nini ?', lang: 'ln', fr: 'Quoi de neuf ?', en: 'What’s new?' },
  { text: 'Karibu !', lang: 'sw', fr: 'Bienvenue', en: 'Welcome' },
  { text: 'Ozali malamu ?', lang: 'ln', fr: 'Tu vas bien ?', en: 'Are you well?' },
  { text: 'Habari yako ?', lang: 'sw', fr: 'Comment vas-tu ?', en: 'How are you?' },
]

export const CHAT: Record<string, { from: 'mama' | 'me'; text: string; fr: string; en: string }[]> = {
  ln: [
    { from: 'mama', text: 'Mbote mwana na ngai ! Ozali malamu ?', fr: 'Bonjour mon enfant ! Tu vas bien ?', en: 'Hello my child! Are you well?' },
    { from: 'me', text: 'Ee mama, nazali malamu. Matondo !', fr: 'Oui maman, je vais bien. Merci !', en: 'Yes mum, I’m fine. Thank you!' },
    { from: 'mama', text: 'Nazali na esengo mingi 🥹', fr: 'Je suis très heureuse', en: 'I’m so happy' },
  ],
  sw: [
    { from: 'mama', text: 'Jambo mtoto wangu ! Habari yako ?', fr: 'Bonjour mon enfant ! Comment vas-tu ?', en: 'Hello my child! How are you?' },
    { from: 'me', text: 'Nzuri mama, asante !', fr: 'Bien maman, merci !', en: 'Good mum, thank you!' },
    { from: 'mama', text: 'Karibu nyumbani 🥹', fr: 'Bienvenue à la maison', en: 'Welcome home' },
  ],
}

const fr = {
  heroTitle: 'Parle enfin la langue de la famille.',
  heroText: 'Les langues du Congo, cinq minutes par jour. Gratuit, sans pub, fait à Kinshasa.',
  start: 'Commencer',
  tryLesson: 'Essayer une leçon',
  continue: 'Continuer mon parcours',
  tapLoba: 'Touche Loba',
  playTitle: 'Ta première leçon, ici.',
  playText: 'Trois questions pour goûter à Loba. Crée ton compte pour continuer.',
  signIn: 'Se connecter',
  langsLabel: 'Langues',
  qMeaning: 'Que veut dire ce mot ?',
  qSay: 'Comment dit-on…',
  qPairs: 'Relie les paires',
  check: 'Vérifier',
  next: 'Continuer',
  right: 'Bravo !',
  wrong: 'Bonne réponse :',
  doneTitle: 'Leçon terminée !',
  doneText: 'Tu viens d’apprendre tes premiers mots. Il en reste plein d’autres.',
  doneCta: 'Créer mon compte',
  again: 'Rejouer',
  chatTitle: 'Des phrases que tu vas vraiment dire.',
  chatText: 'Maman au téléphone, le marché, l’aéroport de N’djili. Les leçons partent de la vraie vie, pas d’un manuel.',
  chatHint: 'Touche un message pour le traduire',
  mamaName: 'Maman',
  online: 'en ligne',
  speakTitle: 'Parle. Loba t’écoute.',
  speakText: 'Écoute chaque mot, ralentis-le, puis réponds au micro. Loba vérifie ta prononciation, et tu oses enfin répondre.',
  speakTap: 'Touche le micro',
  speakGood: 'Malamu mingi !',
  streakTitle: 'Un petit peu, chaque jour.',
  streakText: 'Ta série grandit à chaque leçon. Des quêtes, une ligue chaque semaine et un rappel si tu oublies.',
  streakTap: 'Touche la flamme',
  days: 'jours',
  week: ['L', 'M', 'M', 'J', 'V', 'S', 'D'],
  rumbaTitle: 'Comprends enfin les chansons.',
  rumbaText: 'Le lingala, c’est la langue de la rumba congolaise, inscrite au patrimoine de l’UNESCO. Celle que tes parents écoutent le dimanche.',
  storyEyebrow: 'Nouveau · Masapo',
  storyTitle: 'Les contes de chez nous, comme à la veillée.',
  storyText: 'Des contes du Congo illustrés et animés, à lire en famille. Chaque histoire glisse quelques mots de la langue que tu apprends.',
  storyCta: 'Ouvrir le premier conte',
  storyName: 'Pourquoi le crocodile ne mange pas la poule',
  rumbaTap: 'Tape sur le tam-tam',
  finalTitle: 'Tokende !',
  finalText: 'Allons-y. Ta première leçon prend cinq minutes.',
  footTag: 'Les langues du Congo, pour la diaspora et pour le pays.',
  privacy: 'Confidentialité',
  madeIn: 'Fait à Kinshasa',
  ui: 'Langue',
}

export type LandingKey = keyof typeof fr

const en: typeof fr = {
  heroTitle: 'Finally speak the family language.',
  heroText: 'The languages of Congo, five minutes a day. Free, no ads, made in Kinshasa.',
  start: 'Get started',
  tryLesson: 'Try a lesson',
  continue: 'Continue my path',
  tapLoba: 'Tap Loba',
  playTitle: 'Your first lesson, right here.',
  playText: 'Three questions to get a taste. Create your account to keep going.',
  signIn: 'Sign in',
  langsLabel: 'Languages',
  qMeaning: 'What does this word mean?',
  qSay: 'How do you say…',
  qPairs: 'Match the pairs',
  check: 'Check',
  next: 'Continue',
  right: 'Well done!',
  wrong: 'Correct answer:',
  doneTitle: 'Lesson complete!',
  doneText: 'You just learned your first words. There are plenty more.',
  doneCta: 'Create my account',
  again: 'Play again',
  chatTitle: 'Phrases you’ll actually say.',
  chatText: 'Mum on the phone, the market, N’djili airport. Lessons start from real life, not a textbook.',
  chatHint: 'Tap a message to translate it',
  mamaName: 'Mum',
  online: 'online',
  speakTitle: 'Speak. Loba is listening.',
  speakText: 'Hear every word, slow it down, then answer into the mic. Loba checks your pronunciation, so you finally dare to reply.',
  speakTap: 'Tap the mic',
  speakGood: 'Malamu mingi!',
  streakTitle: 'A little, every day.',
  streakText: 'Your streak grows with every lesson. Quests, a weekly league and a reminder if you forget.',
  streakTap: 'Tap the flame',
  days: 'days',
  week: ['M', 'T', 'W', 'T', 'F', 'S', 'S'],
  rumbaTitle: 'Finally understand the songs.',
  rumbaText: 'Lingala is the language of Congolese rumba, on UNESCO’s heritage list. The music your parents play on Sundays.',
  storyEyebrow: 'New · Masapo',
  storyTitle: 'Our folktales, told like around the fire.',
  storyText: 'Tales from Congo, illustrated and animated, to read as a family. Each story slips in a few words of the language you’re learning.',
  storyCta: 'Open the first tale',
  storyName: 'Why the Crocodile Does Not Eat the Hen',
  rumbaTap: 'Tap the drum',
  finalTitle: 'Tokende!',
  finalText: 'Let’s go. Your first lesson takes five minutes.',
  footTag: 'The languages of Congo, for the diaspora and back home.',
  privacy: 'Privacy',
  madeIn: 'Made in Kinshasa',
  ui: 'Language',
}

export const LANDING = { fr, en }
