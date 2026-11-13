import type { QueryClient } from '@tanstack/react-query'
import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router'
import { curriculumQuery } from '../content/curriculum'
import { Loba } from '../components/Loba'
import { useT } from '../i18n/useT'

export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  // The curriculum is needed everywhere; load it once before any screen renders.
  loader: ({ context }) => context.queryClient.ensureQueryData(curriculumQuery),
  component: Outlet,
  pendingComponent: Pending,
  notFoundComponent: NotFound,
})

function Pending() {
  return (
    <div className="center-screen" role="status">
      <div className="spinner" />
    </div>
  )
}

function NotFound() {
  const { t } = useT()
  return (
    <div className="center-screen">
      <Loba mood="think" />
      <h1>{t('notFound')}</h1>
      <Link to="/" className="btn">
        {t('backHome')}
      </Link>
    </div>
  )
}
