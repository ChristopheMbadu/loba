import '@fontsource-variable/nunito'
import '@fontsource-variable/fredoka'
import './styles/app.css'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRouter, RouterProvider } from '@tanstack/react-router'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { actions } from './game/store'
import { routeTree } from './routeTree.gen'

const queryClient = new QueryClient()

const router = createRouter({
  routeTree,
  basepath: '/learn',
  context: { queryClient },
  defaultPreload: 'intent',
  scrollRestoration: true,
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

// Daily rollover and heart regeneration whenever the learner comes back.
actions.tick()
document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && actions.tick())
setInterval(actions.tick, 60_000)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
