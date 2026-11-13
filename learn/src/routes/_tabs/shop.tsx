import { createFileRoute } from '@tanstack/react-router'
import { useSelector } from '@tanstack/react-store'
import { sfx } from '../../audio/sfx'
import { Art, type ArtName } from '../../components/Art'
import { Rail } from '../../components/Rail'
import { MAX_FREEZES, MAX_HEARTS, PRICES } from '../../game/state'
import { canBuy, type ShopItem } from '../../game/rules'
import { actions, game } from '../../game/store'
import { useNow } from '../../hooks'
import { useT } from '../../i18n/useT'

export const Route = createFileRoute('/_tabs/shop')({ component: ShopPage })

function ShopPage() {
  const { t } = useT()
  const now = useNow(15_000)
  const s = useSelector(game, (s) => s)
  const boostMin = Math.ceil((s.doubleXpUntil - now) / 60000)
  const heartMin = s.heartsRefillAt ? Math.max(1, Math.ceil((s.heartsRefillAt - now) / 60000)) : 0

  const items: { id: ShopItem; art: ArtName; tint: string; title: string; text: string; full: boolean }[] = [
    { id: 'refill', art: 'heart', tint: 'var(--red-tint)', title: t('refillHearts'), text: s.hearts >= MAX_HEARTS ? t('heartsFull') : `${t('refillHeartsText')} ${t('nextHeartIn', { min: heartMin })}`, full: s.hearts >= MAX_HEARTS },
    { id: 'freeze', art: 'freeze', tint: 'var(--sky-tint)', title: t('streakFreeze'), text: t('streakFreezeText', { n: s.freezes, max: MAX_FREEZES }), full: s.freezes >= MAX_FREEZES },
    { id: 'doubleXp', art: 'bolt', tint: 'var(--sun-tint)', title: t('doubleXp'), text: boostMin > 0 ? t('doubleXpActive', { min: boostMin }) : t('doubleXpText'), full: boostMin > 0 },
  ]

  return (
    <div className="page">
      <div>
        <header className="page-head">
          <h1>{t('shopTitle')}</h1>
          <p>{t('shopIntro')}</p>
        </header>
        <div className="stack">
          {items.map((item) => (
            <div className="tile-card" key={item.id}>
              <span className="icon-tile" style={{ '--tint': item.tint } as React.CSSProperties}><Art name={item.art} /></span>
              <span className="body">
                <h3>{item.title}</h3>
                <p>{item.text}</p>
              </span>
              <button
                className="btn small outline"
                disabled={!canBuy(s, item.id, now)}
                aria-label={`${t('buy')} ${item.title} · ${PRICES[item.id]}`}
                title={!item.full && s.gems < PRICES[item.id] ? t('notEnoughGems') : undefined}
                onClick={() => {
                  actions.buy(item.id)
                  sfx('coin')
                }}
              >
                {item.full ? t('owned') : <span className="price"><Art name="gem" /> {PRICES[item.id]}</span>}
              </button>
            </div>
          ))}
        </div>
      </div>
      <Rail />
    </div>
  )
}
