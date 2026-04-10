'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, Globe, Eye, EyeOff } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useLogin } from '@/services/authApi'
import { useToast } from '@/components/ui/Toast'

const loginSchema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
})

type LoginForm = z.infer<typeof loginSchema>

export default function LoginPage() {
  const router = useRouter()
  const { showToast } = useToast()
  const [showPassword, setShowPassword] = useState(false)
  const loginMutation = useLogin()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: 'analyst@numera.ai',
      password: 'Password123!',
    },
  })

  const handleLogin = async (values: LoginForm) => {
    try {
      await loginMutation.mutateAsync(values)
      showToast('Signed in successfully', 'success')
      router.push('/dashboard')
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error ? String((error as { message: string }).message) : 'Login failed'
      showToast(message, 'error')
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12, marginBottom: 8 }}>
            <div style={{
              width: 52, height: 52,
              background: 'linear-gradient(135deg,var(--accent),var(--purple))',
              borderRadius: 14,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 800, fontSize: 26, color: 'white'
            }}>N</div>
          </div>
          <h1>Numera</h1>
          <p>AI-First Financial Spreading Platform</p>
        </div>

        <div className="login-form">
          <button
            className="btn btn-secondary btn-lg"
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => showToast('SSO is not configured in this environment', 'info')}
            type="button"
          >
            <Globe size={18} />
            Sign in with SSO
          </button>

          <div className="login-divider">or continue with credentials</div>

          <form onSubmit={handleSubmit(handleLogin)} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="input-group">
              <label htmlFor="email">Email Address</label>
              <input
                id="email"
                className="input"
                type="email"
                placeholder="analyst@bank.com"
                autoComplete="email"
                {...register('email')}
              />
              {errors.email ? <span style={{ color: 'var(--danger)', fontSize: 12 }}>{errors.email.message}</span> : null}
            </div>

            <div className="input-group">
              <label htmlFor="password">Password</label>
              <div style={{ position: 'relative' }}>
                <input
                  id="password"
                  className="input"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  autoComplete="current-password"
                  style={{ paddingRight: 40 }}
                  {...register('password')}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(v => !v)}
                  style={{
                    position: 'absolute', right: 10, top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'none', border: 'none',
                    cursor: 'pointer', color: 'var(--text-muted)',
                    padding: 2,
                  }}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password ? <span style={{ color: 'var(--danger)', fontSize: 12 }}>{errors.password.message}</span> : null}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 13 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <input type="checkbox" /> Remember me
              </label>
              <a href="#" style={{ color: 'var(--accent)', textDecoration: 'none', fontSize: 13 }}>
                Forgot password?
              </a>
            </div>

            <button type="submit" className="btn btn-primary btn-lg" style={{ width: '100%', justifyContent: 'center' }} disabled={loginMutation.isPending}>
              <Lock size={16} />
              {loginMutation.isPending ? 'Signing In...' : 'Sign In'}
            </button>
          </form>

          <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
            Protected by Multi-Factor Authentication
          </div>
        </div>
      </div>
    </div>
  )
}
