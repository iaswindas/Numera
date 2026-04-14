import { useAuthStore } from '../authStore'

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
    })
  })

  it('sets tokens and marks authenticated', () => {
    useAuthStore.getState().setToken('access-123', 'refresh-456')
    const state = useAuthStore.getState()
    expect(state.accessToken).toBe('access-123')
    expect(state.refreshToken).toBe('refresh-456')
    expect(state.isAuthenticated).toBe(true)
  })

  it('clears all state on logout', () => {
    useAuthStore.getState().setToken('access-123', 'refresh-456')
    useAuthStore.getState().setUser({ id: '1', email: 'test@test.com', fullName: 'Test User', tenantId: 't1', roles: ['ANALYST'] })
    useAuthStore.getState().logout()
    const state = useAuthStore.getState()
    expect(state.accessToken).toBeNull()
    expect(state.refreshToken).toBeNull()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })

  it('sets user data', () => {
    const user = { id: '1', email: 'test@test.com', fullName: 'Test User', tenantId: 't1', roles: ['ANALYST'] }
    useAuthStore.getState().setUser(user)
    expect(useAuthStore.getState().user).toEqual(user)
  })
})
