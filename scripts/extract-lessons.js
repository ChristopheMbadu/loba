// One-time migration of this repository's trusted, static curriculum. Never runs user code.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import vm from 'node:vm';
const source = await readFile(new URL('../index.html', import.meta.url), 'utf8');
const start = source.indexOf('const LANGS=');
const end = source.indexOf('const UI=');
if (start < 0 || end < start) throw new Error('Curriculum markers missing');
const { languages, translations } = vm.runInNewContext(source.slice(start, end) + '\n({languages: LANGS, translations: EN})', {}, { timeout: 1000 });
const plain = value => value.replace(/<[^>]*>/g, '');
const lessons = Object.values(languages).flatMap(language => language.units.map((unit, index) => ({
  id: `${language.code}-${unit.id}`,
  language: language.code,
  position: index + 1,
  title: unit.title,
  titleEn: translations[unit.title] || '',
  icon: unit.icon,
  tip: plain(unit.tip || ''),
  words: unit.words.map(([term, fr]) => ({ term, fr, en: translations[fr] || '' })),
  sentences: unit.sentences.map(([fr, answer, distractors]) => ({ fr, en: translations[fr] || '', answer, distractors })),
})));
await mkdir(new URL('../data/', import.meta.url), { recursive: true });
await writeFile(new URL('../data/lessons.json', import.meta.url), JSON.stringify(lessons, null, 2) + '\n');
console.log(`Extracted ${lessons.length} units. Missing English translations remain blank for editorial review.`);
