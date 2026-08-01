"use client"

import type React from "react"
import { useEffect, useMemo, useState } from "react"
import {
  Shield,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Search,
  Filter,
  Clock,
  Eye,
  FileText,
  BarChart3,
  Users,
  Settings,
  LogOut,
  Sun,
  Moon,
  Bell,
  ChevronDown,
  TrendingUp,
  Activity,
  CreditCard,
  MapPin,
  Landmark,
  Lock,
  Mail,
  User,
} from "lucide-react"

type Theme = "light" | "dark"

function useTheme() {
  const [theme, setTheme] = useState<Theme>("light")
  useEffect(() => {
    const stored = localStorage.getItem("centinela-theme") as Theme | null
    const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches
    setTheme(stored ?? (prefersDark ? "dark" : "light"))
  }, [])
  useEffect(() => {
    const root = document.documentElement
    root.classList.remove("light", "dark")
    root.classList.add(theme)
    localStorage.setItem("centinela-theme", theme)
  }, [theme])
  const toggle = () => setTheme((t) => (t === "dark" ? "light" : "dark"))
  return { theme, toggle }
}

function ThemeToggle({ theme, toggle }: { theme: Theme; toggle: () => void }) {
  return (
    <button
      onClick={toggle}
      className="flex size-9 items-center justify-center rounded-xl border border-border bg-secondary text-foreground transition-all duration-200 hover:bg-secondary/70 active:scale-95"
    >
      {theme === "dark" ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </button>
  )
}

interface FraudCase {
  id: string
  transactionId: string
  cuentaId: string
  score: number
  umbral: number
  estado: string
  fechaApertura: string
  explicacion: string
  reglasActivadas: string[]
}

interface Transaction {
  id: string
  cuentaId: string
  monto: number
  moneda: string
  marcaTiempo: string
  ubicacion: string
  comercioId: string
  score: number
  marcada: boolean
}

interface Metrics {
  totalTransacciones: number
  transaccionesMarcadas: number
  tasaFraude: number
  casosAbiertos: number
  scorePromedio: number
}

const MOCK_CASES: FraudCase[] = [
  {
    id: "CASE-001",
    transactionId: "TXN-100234",
    cuentaId: "ACC-001",
    score: 85,
    umbral: 60,
    estado: "ABIERTO",
    fechaApertura: new Date(Date.now() - 3600000).toISOString(),
    explicacion: "Transaccion marcada con score 85 (umbral: 60).\n\nSe detectaron 4 transacciones de esta cuenta en los ultimos 5 minutos, cuando el limite es de 3 (+35 puntos).\n\nEl monto de $4.200.000 supera en 84x el promedio historico de la cuenta ($50.000) (+30 puntos).\n\nLa transaccion anterior de esta cuenta se origino en Medellin hace 11 minutos; esta se origina en Madrid, a 8.000 km (+20 puntos).",
    reglasActivadas: ["VELOCITY", "AMOUNT", "GEO_IMPOSSIBLE"],
  },
  {
    id: "CASE-002",
    transactionId: "TXN-100233",
    cuentaId: "ACC-003",
    score: 72,
    umbral: 60,
    estado: "EN_REVISION",
    fechaApertura: new Date(Date.now() - 7200000).toISOString(),
    explicacion: "Transaccion marcada con score 72 (umbral: 60).\n\nSe detectaron 3 transacciones de esta cuenta en los ultimos 5 minutos (+35 puntos).\n\nEl comercio destino esta en la lista de entidades de riesgo (+20 puntos).\n\nEl monto de $850.000 supera en 12x el promedio historico (+17 puntos).",
    reglasActivadas: ["VELOCITY", "MERCHANT_RISK", "AMOUNT"],
  },
  {
    id: "CASE-003",
    transactionId: "TXN-100230",
    cuentaId: "ACC-002",
    score: 65,
    umbral: 60,
    estado: "CERRADO",
    fechaApertura: new Date(Date.now() - 86400000).toISOString(),
    explicacion: "Transaccion marcada con score 65 (umbral: 60).\n\nLa transaccion anterior de esta cuenta se origino en Bogota; esta se origina en Panama, con 30 minutos de diferencia (+25 puntos).\n\nEl monto de $1.200.000 supera en 15x el promedio historico (+20 puntos).\n\nComercio categorizado como de alto riesgo (+20 puntos).",
    reglasActivadas: ["GEO_IMPOSSIBLE", "AMOUNT", "MERCHANT_RISK"],
  },
]

const MOCK_TRANSACTIONS: Transaction[] = [
  { id: "TXN-100234", cuentaId: "ACC-001", monto: 4200000, moneda: "USD", marcaTiempo: new Date(Date.now() - 300000).toISOString(), ubicacion: "Madrid, ES", comercioId: "darkmarket", score: 85, marcada: true },
  { id: "TXN-100233", cuentaId: "ACC-003", monto: 850000, moneda: "USD", marcaTiempo: new Date(Date.now() - 600000).toISOString(), ubicacion: "Bogota, CO", comercioId: "gambling", score: 72, marcada: true },
  { id: "TXN-100232", cuentaId: "ACC-002", monto: 15000, moneda: "USD", marcaTiempo: new Date(Date.now() - 900000).toISOString(), ubicacion: "Medellin, CO", comercioId: "retail", score: 15, marcada: false },
  { id: "TXN-100231", cuentaId: "ACC-001", monto: 50000, moneda: "USD", marcaTiempo: new Date(Date.now() - 1200000).toISOString(), ubicacion: "Medellin, CO", comercioId: "restaurant", score: 10, marcada: false },
  { id: "TXN-100230", cuentaId: "ACC-002", monto: 1200000, moneda: "USD", marcaTiempo: new Date(Date.now() - 1800000).toISOString(), ubicacion: "Panama City, PA", comercioId: "crypto-mixer", score: 65, marcada: true },
  { id: "TXN-100229", cuentaId: "ACC-004", monto: 25000, moneda: "USD", marcaTiempo: new Date(Date.now() - 2400000).toISOString(), ubicacion: "Lima, PE", comercioId: "grocery", score: 5, marcada: false },
  { id: "TXN-100228", cuentaId: "ACC-003", monto: 45000, moneda: "USD", marcaTiempo: new Date(Date.now() - 3600000).toISOString(), ubicacion: "Santiago, CL", comercioId: "restaurant", score: 8, marcada: false },
]

const currency = (n: number) => n.toLocaleString("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 })
const formatDate = (s: string) => new Date(s).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" })

function LoginView({ onLogin, theme, toggleTheme }: { onLogin: (email: string) => void; theme: Theme; toggleTheme: () => void }) {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-background px-4 py-10">
      <div className="absolute right-4 top-4 sm:right-6 sm:top-6">
        <ThemeToggle theme={theme} toggle={toggleTheme} />
      </div>
      <div className="w-full max-w-md">
        <div className="mb-6 flex flex-col items-center text-center">
          <div className="mb-4 flex size-14 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-lg">
            <Shield className="size-7" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground text-balance">
            Centinela
          </h1>
          <p className="mt-1 text-sm text-muted-foreground text-pretty">
            Motor de deteccion de fraude transaccional en tiempo real
          </p>
        </div>

        <div className="rounded-3xl border border-border bg-card p-6 shadow-sm sm:p-8">
          <form onSubmit={(e) => { e.preventDefault(); onLogin(email) }} className="flex flex-col gap-4">
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-foreground">Email</span>
              <span className="relative block">
                <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-muted-foreground">
                  <Mail className="size-4" />
                </span>
                <input
                  type="email"
                  placeholder="analista@centinela.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="h-11 w-full rounded-xl border border-input bg-background pl-10 pr-3 text-sm text-foreground outline-none transition-all duration-200 placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/30"
                />
              </span>
            </label>
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-foreground">Password</span>
              <span className="relative block">
                <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-muted-foreground">
                  <Lock className="size-4" />
                </span>
                <input
                  type="password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="h-11 w-full rounded-xl border border-input bg-background pl-10 pr-3 text-sm text-foreground outline-none transition-all duration-200 placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/30"
                />
              </span>
            </label>
            <button type="submit" className="mt-1 flex h-11 items-center justify-center gap-2 rounded-xl bg-primary text-sm font-semibold text-primary-foreground shadow-sm transition-all duration-200 hover:opacity-90 active:scale-[0.98]">
              <Lock className="size-4" />
              Iniciar sesion
            </button>
          </form>

          <div className="my-5 flex items-center gap-3">
            <span className="h-px flex-1 bg-border" />
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Acceso rapido demo</span>
            <span className="h-px flex-1 bg-border" />
          </div>

          <div className="flex flex-col gap-3">
            <button onClick={() => onLogin("analista@centinela.com")} className="flex items-center justify-between rounded-xl border border-border bg-secondary px-4 py-3 text-left transition-all duration-200 hover:border-ring hover:bg-secondary/70 active:scale-[0.99]">
              <span className="flex items-center gap-3">
                <span className="flex size-9 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
                  <Shield className="size-4" />
                </span>
                <span>
                  <span className="block text-sm font-semibold text-foreground">Analista de Fraude</span>
                  <span className="block text-xs text-muted-foreground">Revision de casos</span>
                </span>
              </span>
              <User className="size-4 text-muted-foreground" />
            </button>
            <button onClick={() => onLogin("admin@centinela.com")} className="flex items-center justify-between rounded-xl border border-border bg-secondary px-4 py-3 text-left transition-all duration-200 hover:border-ring hover:bg-secondary/70 active:scale-[0.99]">
              <span className="flex items-center gap-3">
                <span className="flex size-9 items-center justify-center rounded-full bg-foreground text-xs font-bold text-background">
                  <Settings className="size-4" />
                </span>
                <span>
                  <span className="block text-sm font-semibold text-foreground">Administrador</span>
                  <span className="block text-xs text-muted-foreground">Configuracion del sistema</span>
                </span>
              </span>
              <Settings className="size-4 text-muted-foreground" />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function TopBar({ user, onLogout, badge, theme, toggleTheme }: { user: { name: string; role: string }; onLogout: () => void; badge?: React.ReactNode; theme: Theme; toggleTheme: () => void }) {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-card/80 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <div className="flex items-center gap-2.5">
          <span className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Shield className="size-5" />
          </span>
          <span className="hidden text-base font-bold tracking-tight text-foreground sm:block">Centinela</span>
          {badge}
        </div>
        <div className="flex items-center gap-3">
          <ThemeToggle theme={theme} toggle={toggleTheme} />
          <button className="relative flex size-9 items-center justify-center rounded-xl border border-border bg-secondary text-foreground transition-all duration-200 hover:bg-secondary/70">
            <Bell className="size-4" />
            <span className="absolute -right-1 -top-1 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground">3</span>
          </button>
          <div className="hidden text-right sm:block">
            <p className="text-sm font-semibold leading-tight text-foreground">{user.name}</p>
            <p className="text-xs text-muted-foreground">{user.role === "admin" ? "Administrador" : "Analista de Fraude"}</p>
          </div>
          <button onClick={onLogout} className="flex items-center gap-1.5 rounded-xl border border-border bg-secondary px-3 py-2 text-sm font-medium text-foreground transition-all duration-200 hover:bg-secondary/70 active:scale-95">
            <LogOut className="size-4" />
            <span className="hidden sm:inline">Salir</span>
          </button>
        </div>
      </div>
    </header>
  )
}

function Metric({ icon, label, value, accent }: { icon: React.ReactNode; label: string; value: string; accent?: boolean }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="flex items-center justify-between">
        <span className={`flex size-10 items-center justify-center rounded-xl ${accent ? "bg-accent/15 text-accent" : "bg-secondary text-foreground"}`}>{icon}</span>
      </div>
      <p className="mt-4 text-2xl font-bold tracking-tight text-card-foreground">{value}</p>
      <p className="text-sm text-muted-foreground">{label}</p>
    </div>
  )
}

function AnalystDashboard({ cases, transactions, onSelectCase, onLogout, theme, toggleTheme }: { cases: FraudCase[]; transactions: Transaction[]; onSelectCase: (c: FraudCase) => void; onLogout: () => void; theme: Theme; toggleTheme: () => void }) {
  const [filter, setFilter] = useState<string>("all")
  const [search, setSearch] = useState("")

  const filtered = cases.filter(c => {
    if (filter !== "all" && c.estado !== filter) return false
    if (search && !c.id.toLowerCase().includes(search.toLowerCase()) && !c.cuentaId.toLowerCase().includes(search.toLowerCase())) return false
    return true
  })

  const metrics: Metrics = {
    totalTransacciones: transactions.length,
    transaccionesMarcadas: transactions.filter(t => t.marcada).length,
    tasaFraude: transactions.length > 0 ? (transactions.filter(t => t.marcada).length / transactions.length * 100) : 0,
    casosAbiertos: cases.filter(c => c.estado === "ABIERTO").length,
    scorePromedio: cases.length > 0 ? cases.reduce((sum, c) => sum + c.score, 0) / cases.length : 0,
  }

  return (
    <div className="min-h-screen bg-background">
      <TopBar user={{ name: "Santiago G.", role: "analyst" }} onLogout={onLogout} theme={theme} toggleTheme={toggleTheme}
        badge={<span className="ml-1 flex items-center gap-1 rounded-full bg-primary px-2.5 py-1 text-xs font-semibold text-primary-foreground"><Shield className="size-3" /> Analista</span>} />
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 sm:py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Panel de Analista</h1>
          <p className="text-sm text-muted-foreground">Monitoreo y gestion de casos de fraude en tiempo real</p>
        </div>

        <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Metric icon={<Activity className="size-5" />} label="Transacciones totales" value={String(metrics.totalTransacciones)} />
          <Metric icon={<AlertTriangle className="size-5" />} label="Transacciones marcadas" value={String(metrics.transaccionesMarcadas)} accent />
          <Metric icon={<TrendingUp className="size-5" />} label="Tasa de fraude" value={`${metrics.tasaFraude.toFixed(1)}%`} />
          <Metric icon={<FileText className="size-5" />} label="Casos abiertos" value={String(metrics.casosAbiertos)} />
        </div>

        <div className="mb-6 rounded-3xl border border-border bg-card p-6">
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
              <span className="flex size-9 items-center justify-center rounded-xl bg-secondary text-foreground"><FileText className="size-4" /></span>
              <h2 className="text-base font-semibold text-card-foreground">Casos de fraude</h2>
            </div>
            <div className="flex items-center gap-3">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Buscar caso..."
                  className="h-10 w-full rounded-xl border border-input bg-background pl-9 pr-3 text-sm text-foreground outline-none transition-all duration-200 placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/30 sm:w-64" />
              </div>
              <select value={filter} onChange={(e) => setFilter(e.target.value)}
                className="h-10 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none transition-all duration-200 focus:border-ring focus:ring-2 focus:ring-ring/30">
                <option value="all">Todos</option>
                <option value="ABIERTO">Abiertos</option>
                <option value="EN_REVISION">En revision</option>
                <option value="CERRADO">Cerrados</option>
              </select>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[700px] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="py-3 pr-4 font-medium">Caso</th>
                  <th className="py-3 pr-4 font-medium">Transaccion</th>
                  <th className="py-3 pr-4 font-medium">Cuenta</th>
                  <th className="py-3 pr-4 font-medium">Score</th>
                  <th className="py-3 pr-4 font-medium">Estado</th>
                  <th className="py-3 pr-4 font-medium">Fecha</th>
                  <th className="py-3 font-medium">Accion</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((c) => (
                  <tr key={c.id} className="border-b border-border/60 transition-colors last:border-0 hover:bg-secondary/50">
                    <td className="py-3 pr-4 font-mono text-xs text-muted-foreground">{c.id}</td>
                    <td className="py-3 pr-4 font-mono text-xs text-muted-foreground">{c.transactionId}</td>
                    <td className="py-3 pr-4 font-medium text-card-foreground">{c.cuentaId}</td>
                    <td className="py-3 pr-4">
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold ${c.score >= 80 ? "bg-destructive/15 text-destructive" : c.score >= 60 ? "bg-yellow-500/15 text-yellow-600" : "bg-accent/15 text-accent"}`}>
                        {c.score}
                      </span>
                    </td>
                    <td className="py-3 pr-4">
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold ${c.estado === "ABIERTO" ? "bg-destructive/15 text-destructive" : c.estado === "EN_REVISION" ? "bg-yellow-500/15 text-yellow-600" : "bg-accent/15 text-accent"}`}>
                        {c.estado === "ABIERTO" ? "Abierto" : c.estado === "EN_REVISION" ? "En revision" : "Cerrado"}
                      </span>
                    </td>
                    <td className="py-3 pr-4 text-xs text-muted-foreground">{formatDate(c.fechaApertura)}</td>
                    <td className="py-3">
                      <button onClick={() => onSelectCase(c)} className="flex items-center gap-1 rounded-lg bg-primary/10 px-3 py-1.5 text-xs font-medium text-primary transition-all hover:bg-primary/20">
                        <Eye className="size-3" /> Ver
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="rounded-3xl border border-border bg-card p-6">
          <div className="mb-4 flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-xl bg-secondary text-foreground"><BarChart3 className="size-4" /></span>
            <h2 className="text-base font-semibold text-card-foreground">Transacciones recientes</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[600px] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="py-3 pr-4 font-medium">ID</th>
                  <th className="py-3 pr-4 font-medium">Cuenta</th>
                  <th className="py-3 pr-4 font-medium">Monto</th>
                  <th className="py-3 pr-4 font-medium">Ubicacion</th>
                  <th className="py-3 pr-4 font-medium">Score</th>
                  <th className="py-3 font-medium">Estado</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((t) => (
                  <tr key={t.id} className="border-b border-border/60 transition-colors last:border-0 hover:bg-secondary/50">
                    <td className="py-3 pr-4 font-mono text-xs text-muted-foreground">{t.id}</td>
                    <td className="py-3 pr-4 font-medium text-card-foreground">{t.cuentaId}</td>
                    <td className="py-3 pr-4 font-mono font-semibold text-card-foreground">{currency(t.monto)}</td>
                    <td className="py-3 pr-4 text-xs text-muted-foreground">{t.ubicacion}</td>
                    <td className="py-3 pr-4">
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold ${t.marcada ? "bg-destructive/15 text-destructive" : "bg-accent/15 text-accent"}`}>
                        {t.score}
                      </span>
                    </td>
                    <td className="py-3">
                      {t.marcada ? <XCircle className="size-4 text-destructive" /> : <CheckCircle className="size-4 text-accent" />}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  )
}

function CaseDetailModal({ case: fraudCase, onClose }: { case: FraudCase; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-2xl rounded-3xl border border-border bg-card p-6 shadow-xl max-h-[90vh] overflow-y-auto">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-card-foreground">Detalle del Caso {fraudCase.id}</h2>
          <button onClick={onClose} className="flex size-8 items-center justify-center rounded-lg bg-secondary text-foreground hover:bg-secondary/70">
            <XCircle className="size-4" />
          </button>
        </div>

        <div className="mb-4 grid grid-cols-2 gap-4">
          <div className="rounded-xl bg-secondary p-3">
            <p className="text-xs text-muted-foreground">Transaccion</p>
            <p className="font-mono text-sm font-semibold">{fraudCase.transactionId}</p>
          </div>
          <div className="rounded-xl bg-secondary p-3">
            <p className="text-xs text-muted-foreground">Cuenta</p>
            <p className="font-mono text-sm font-semibold">{fraudCase.cuentaId}</p>
          </div>
          <div className="rounded-xl bg-secondary p-3">
            <p className="text-xs text-muted-foreground">Score / Umbral</p>
            <p className="font-mono text-sm font-semibold">{fraudCase.score} / {fraudCase.umbral}</p>
          </div>
          <div className="rounded-xl bg-secondary p-3">
            <p className="text-xs text-muted-foreground">Estado</p>
            <p className="text-sm font-semibold">{fraudCase.estado}</p>
          </div>
        </div>

        <div className="mb-4">
          <h3 className="mb-2 text-sm font-semibold text-card-foreground">Reglas activadas</h3>
          <div className="flex flex-wrap gap-2">
            {fraudCase.reglasActivadas.map((r) => (
              <span key={r} className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">{r}</span>
            ))}
          </div>
        </div>

        <div>
          <h3 className="mb-2 text-sm font-semibold text-card-foreground">Explicacion del caso</h3>
          <div className="rounded-xl bg-secondary p-4">
            <pre className="whitespace-pre-wrap text-sm text-card-foreground font-mono">{fraudCase.explicacion}</pre>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function CentinelaApp() {
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)
  const [view, setView] = useState<"login" | "analyst" | "admin">("login")
  const [selectedCase, setSelectedCase] = useState<FraudCase | null>(null)
  const { theme, toggle: toggleTheme } = useTheme()

  const handleLogin = (email: string) => {
    if (email.includes("admin")) {
      setCurrentUserId("admin")
      setView("admin")
    } else {
      setCurrentUserId("analyst")
      setView("analyst")
    }
  }

  const handleLogout = () => {
    setCurrentUserId(null)
    setView("login")
  }

  if (view === "login") {
    return <LoginView onLogin={handleLogin} theme={theme} toggleTheme={toggleTheme} />
  }

  return (
    <>
      <AnalystDashboard
        cases={MOCK_CASES}
        transactions={MOCK_TRANSACTIONS}
        onSelectCase={setSelectedCase}
        onLogout={handleLogout}
        theme={theme}
        toggleTheme={toggleTheme}
      />
      {selectedCase && <CaseDetailModal case={selectedCase} onClose={() => setSelectedCase(null)} />}
    </>
  )
}
