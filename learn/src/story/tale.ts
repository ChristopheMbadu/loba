import type { UiLang } from '../content/types'

/**
 * A folktale for the story book. The telling is in French and English; a handful of words
 * appear in the language being learned, so a tale is also a small lesson.
 *
 * The same shape can come from Loba Studio later: every page has its text and, optionally,
 * the id of a narrator's recording (played from /api/v1/audio/:id like word recordings).
 */
export type WordKey = 'croc' | 'hen' | 'river' | 'sibling' | 'water' | 'eggs'

export interface TalePage {
  /** `{croc}`, `{hen}`… are replaced by the word in the learner's language. */
  text: Record<UiLang, string>
  audio?: string
}

export interface Tale {
  id: string
  title: Record<UiLang, string>
  origin: Record<UiLang, string>
  source: Record<UiLang, string>
  moral: Record<UiLang, string>
  words: Record<WordKey, { meaning: Record<UiLang, string>; in: Record<string, string> }>
  pages: TalePage[]
}

/**
 * A Kongo tale from the mouth of the Congo river, retold from the version written down by
 * R. E. Dennett in "Notes on the Folklore of the Fjort" (1898).
 */
export const CROCODILE_AND_HEN: Tale = {
  id: 'ngando-na-nsoso',
  title: {
    fr: 'Pourquoi le crocodile ne mange pas la poule',
    en: 'Why the Crocodile Does Not Eat the Hen',
  },
  origin: { fr: 'Un conte kongo', en: 'A Kongo tale' },
  source: {
    fr: 'Conte kongo, raconté d’après la version notée par R. E. Dennett en 1898.',
    en: 'A Kongo tale, retold from the version written down by R. E. Dennett in 1898.',
  },
  moral: {
    fr: 'Avant de te fâcher, cherche ce qui te rapproche de l’autre.',
    en: 'Before you get angry, look for what you share with the other.',
  },
  words: {
    croc: { meaning: { fr: 'le crocodile', en: 'the crocodile' }, in: { ln: 'Ngando', sw: 'Mamba' } },
    hen: { meaning: { fr: 'la poule', en: 'the hen' }, in: { ln: 'Nsoso', sw: 'Kuku' } },
    river: { meaning: { fr: 'le fleuve', en: 'the river' }, in: { ln: 'ebale', sw: 'mto' } },
    sibling: { meaning: { fr: 'frère, sœur', en: 'brother, sister' }, in: { ln: 'Ndeko', sw: 'Ndugu' } },
    water: { meaning: { fr: 'l’eau', en: 'water' }, in: { ln: 'mai', sw: 'maji' } },
    eggs: { meaning: { fr: 'les œufs', en: 'eggs' }, in: { ln: 'maki', sw: 'mayai' } },
  },
  pages: [
    {
      text: {
        fr: 'Chaque matin, {hen} la poule descendait au bord du fleuve, {river}, pour picorer son déjeuner. Le soleil se levait, l’eau chantait, et tout allait bien.',
        en: 'Every morning, {hen} the hen walked down to the river, {river}, to peck at her breakfast. The sun came up, the water sang, and all was well.',
      },
    },
    {
      text: {
        fr: 'Mais un jour, l’eau se mit à bouger. Deux yeux. Un long museau. C’était {croc} le crocodile ! « Petite poule, dit-il en ouvrant grand la gueule, je vais te manger ! »',
        en: 'But one day, the water began to stir. Two eyes. One long snout. It was {croc} the crocodile! “Little hen,” he said, opening his jaws wide, “I am going to eat you!”',
      },
    },
    {
      text: {
        fr: '{hen} ne s’enfuit pas. Elle cria de toutes ses forces : « {sibling} ! Mon frère, ne fais pas ça ! » {croc} referma la gueule, tout étonné. Son frère ? Il replongea sans dire un mot.',
        en: '{hen} did not run. She cried with all her might: “{sibling}! My brother, don’t do that!” {croc} shut his jaws, astonished. Her brother? He sank back without a word.',
      },
    },
    {
      text: {
        fr: 'Le lendemain il revint, bien décidé à la croquer. Mais la poule cria encore : « {sibling} ! Ne fais pas ça ! » « Comment serais-je son frère ? grogna-t-il. Elle vit sur la terre, et moi dans l’eau, {water} ! »',
        en: 'The next day he came back, determined to gobble her up. But the hen cried again: “{sibling}! Don’t do that!” “How can I be her brother?” he grumbled. “She lives on land, and I live in the water, {water}!”',
      },
    },
    {
      text: {
        fr: 'Cette question l’empêchait de dormir. Alors {croc} sortit du fleuve et partit demander la réponse à Nzambi, qui sait toutes choses. En chemin, il croisa son ami Mbambi, le grand lézard.',
        en: 'The question kept him awake. So {croc} left the river and set off to ask Nzambi, who knows all things. On the way he met his friend Mbambi, the great lizard.',
      },
    },
    {
      text: {
        fr: '« N’y va pas, dit Mbambi, tu montrerais seulement ton ignorance ! Écoute : le canard vit dans l’eau et pond des œufs, {eggs}. La tortue aussi. Moi aussi, je ponds des œufs. La poule en pond. Et toi aussi, mon nigaud d’ami ! »',
        en: '“Don’t go,” said Mbambi, “you will only show how little you know! Listen: the duck lives in the water and lays eggs, {eggs}. So does the turtle. I lay eggs too. The hen lays them. And so do you, my silly friend!”',
      },
    },
    {
      text: {
        fr: '« Alors, d’une certaine façon, nous sommes tous frères. » {croc} rentra au fleuve en souriant. Et voilà pourquoi, depuis ce jour, le crocodile ne mange pas la poule.',
        en: '“So, in a way, we are all brothers.” {croc} went home to the river, smiling. And that is why, from that day to this, the crocodile does not eat the hen.',
      },
    },
  ],
}

export const TALES = [CROCODILE_AND_HEN]

/** Splits a page into plain text and the words to show in the learner's language. */
export function tokens(text: string): (string | { key: WordKey })[] {
  return text.split(/(\{\w+\})/).filter(Boolean).map((part) => (part.startsWith('{') ? { key: part.slice(1, -1) as WordKey } : part))
}
