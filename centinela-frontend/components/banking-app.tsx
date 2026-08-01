"use client"

import type React from "react"

import { useEffect, useMemo, useState } from "react"
import {
  Landmark,
  Lock,
  Mail,
  User,
  Shield,
  Wallet,
  Send,
  ArrowUpRight,
  ArrowDownLeft,
  Eye,
  EyeOff,
  LogOut,
  Users,
  Receipt,
  TrendingUp,
  Search,
  CircleCheck,
  CircleAlert,
  X,
  CreditCard,
  Sun,
  Moon,
} from "lucide-react"

/* -------------------------------------------------------------------------- */
/*  Theme (light / dark)                                                      */
/* -------------------------------------------------------------------------- */

type Theme = "light" | "dark"

function useTheme() {
  const [theme, setTheme] = useState<Theme>("light")

  useEffect(() => {
    const stored = localStorage.getItem("nb-theme") as Theme | null
    const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches
    setTheme(stored ?? (prefersDark ? "dark" : "light"))
  }, [])

  useEffect(() => {
    const root = document.documentElement
    root.classList.remove("light", "dark")
    root.classList.add(theme)
    localStorage.setItem("nb-theme", theme)
  }, [theme])

  const toggle = () => setTheme((t) => (t === "dark" ? "light" : "dark"))
  return { theme, toggle }
}

function ThemeToggle({ theme, toggle }: { theme: Theme; toggle: () => void }) {
  return (
    <button
      onClick={toggle}
      aria-label={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
      className="flex size-9 items-center justify-center rounded-xl border border-border bg-secondary text-foreground transition-all duration-200 hover:bg-secondary/70 active:scale-95"
    >
      {theme === "dark" ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </button>
  )
}

/* -------------------------------------------------------------------------- */
/*  Types                                                                     */
/* -------------------------------------------------------------------------- */

type Role = "client" | "admin"

interface Account {
  id: string
  name: string
  email: string
  accountNumber: string
  balance: number
  role: Role
  color: string
}

interface Transaction {
  id: string
  fromId: string
  toId: string
  fromName: string
  toName: string
  amount: number
  timestamp: number
}

type View = "login" | "dashboard" | "admin"

/* -------------------------------------------------------------------------- */
/*  Mock data                                                                 */
/* -------------------------------------------------------------------------- */

const INITIAL_USERS: Account[] = [
  {
    id: "u1",
    name: "Juan Dela Cruz",
    email: "juan@northwind.com",
    accountNumber: "NB-1042-8871",
    balance: 5420.5,
    role: "client",
    color: "oklch(0.34 0.055 262)",
  },
  {
    id: "u2",
    name: "Maria Santos",
    email: "maria@northwind.com",
    accountNumber: "NB-2093-4410",
    balance: 12850.0,
    role: "client",
    color: "oklch(0.55 0.07 258)",
  },
  {
    id: "u3",
    name: "Carlos Reyes",
    email: "carlos@northwind.com",
    accountNumber: "NB-3388-1256",
    balance: 890.75,
    role: "client",
    color: "oklch(0.5 0.09 200)",
  },
  {
    id: "admin",
    name: "System Admin",
    email: "admin@northwind.com",
    accountNumber: "NB-0000-0001",
    balance: 0,
    role: "admin",
    color: "oklch(0.65 0.12 30)",
  },
]

const now = Date.now()
const HOUR = 1000 * 60 * 60

const INITIAL_TRANSACTIONS: Transaction[] = [
  {
    id: "TXN-100234",
    fromId: "u2",
    toId: "u1",
    fromName: "Maria Santos",
    toName: "Juan Dela Cruz",
    amount: 1200,
    timestamp: now - HOUR * 2,
  },
  {
    id: "TXN-100233",
    fromId: "u1",
    toId: "u3",
    fromName: "Juan Dela Cruz",
    toName: "Carlos Reyes",
    amount: 350.5,
    timestamp: now - HOUR * 26,
  },
  {
    id: "TXN-100232",
    fromId: "u3",
    toId: "u2",
    fromName: "Carlos Reyes",
    toName: "Maria Santos",
    amount: 75,
    timestamp: now - HOUR * 50,
  },
  {
    id: "TXN-100231",
    fromId: "u2",
    toId: "u1",
    fromName: "Maria Santos",
    toName: "Juan Dela Cruz",
    amount: 500,
    timestamp: now - HOUR * 74,
  },
]

/* -------------------------------------------------------------------------- */
/*  Helpers                                                                   */
/* -------------------------------------------------------------------------- */

const currency = (n: number) =>
  n.toLocaleString("en-US", { style: "currency", currency: "USD" })

const formatDate = (ts: number) =>
  new Date(ts).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  })

const initials = (name: string) =>
  name
    .split(" ")
    .map((w) => w[0])
    .slice(0, 2)
    .join("")

/* -------------------------------------------------------------------------- */
/*  Toast                                                                     */
/* -------------------------------------------------------------------------- */

interface Toast {
  id: number
  type: "success" | "error"
  message: string
}

function ToastStack({ toasts, dismiss }: { toasts: Toast[]; dismiss: (id: number) => void }) {
  return (
    <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex flex-col items-center gap-2 px-4">
      {toasts.map((t) => (
        <div
          key={t.id}
          role="status"
          className="pointer-events-auto flex w-full max-w-sm items-center gap-3 rounded-2xl border border-border bg-card px-4 py-3 shadow-lg animate-in fade-in slide-in-from-top-2"
        >
          <span
            className={`flex size-8 shrink-0 items-center justify-center rounded-full ${t.type === "success"
                ? "bg-accent/15 text-accent"
                : "bg-destructive/15 text-destructive"
              }`}
          >
            {t.type === "success" ? (
              <CircleCheck className="size-5" />
            ) : (
              <CircleAlert className="size-5" />
            )}
          </span>
          <p className="flex-1 text-sm font-medium text-card-foreground text-pretty">{t.message}</p>
          <button
            onClick={() => dismiss(t.id)}
            aria-label="Dismiss notification"
            className="text-muted-foreground transition-colors hover:text-foreground"
          >
            <X className="size-4" />
          </button>
        </div>
      ))}
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/*  Small UI primitives                                                       */
/* -------------------------------------------------------------------------- */

function Field({
  icon,
  label,
  ...props
}: { icon: React.ReactNode; label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-foreground">{label}</span>
      <span className="relative block">
        <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-muted-foreground">
          {icon}
        </span>
        <input
          {...props}
          className="h-11 w-full rounded-xl border border-input bg-background pl-10 pr-3 text-sm text-foreground outline-none transition-all duration-200 placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/30"
        />
      </span>
    </label>
  )
}

/* -------------------------------------------------------------------------- */
/*  View: Login                                                               */
/* -------------------------------------------------------------------------- */

function LoginView({
  onLogin,
  onQuickLogin,
  theme,
  toggleTheme,
}: {
  onLogin: (email: string) => void
  onQuickLogin: (id: string) => void
  theme: Theme
  toggleTheme: () => void
}) {
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
            <Landmark className="size-7" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground text-balance">
            Welcome to SJSJS Bank
          </h1>
          <p className="mt-1 text-sm text-muted-foreground text-pretty">
            Secure online banking, reimagined for you.
          </p>
        </div>

        <div className="rounded-3xl border border-border bg-card p-6 shadow-sm sm:p-8">
          <form
            onSubmit={(e) => {
              e.preventDefault()
              onLogin(email)
            }}
            className="flex flex-col gap-4"
          >
            <Field
              icon={<Mail className="size-4" />}
              label="Email address"
              type="email"
              placeholder="you@northwind.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <Field
              icon={<Lock className="size-4" />}
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="submit"
              className="mt-1 flex h-11 items-center justify-center gap-2 rounded-xl bg-primary text-sm font-semibold text-primary-foreground shadow-sm transition-all duration-200 hover:opacity-90 active:scale-[0.98]"
            >
              <Lock className="size-4" />
              Sign in securely
            </button>
          </form>

          <div className="my-5 flex items-center gap-3">
            <span className="h-px flex-1 bg-border" />
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Demo quick access
            </span>
            <span className="h-px flex-1 bg-border" />
          </div>

          <div className="flex flex-col gap-3">
            <button
              onClick={() => onQuickLogin("u1")}
              className="flex items-center justify-between rounded-xl border border-border bg-secondary px-4 py-3 text-left transition-all duration-200 hover:border-ring hover:bg-secondary/70 active:scale-[0.99]"
            >
              <span className="flex items-center gap-3">
                <span className="flex size-9 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
                  JD
                </span>
                <span>
                  <span className="block text-sm font-semibold text-foreground">Login as User (Juan)</span>
                  <span className="block text-xs text-muted-foreground">Client dashboard</span>
                </span>
              </span>
              <User className="size-4 text-muted-foreground" />
            </button>

            <button
              onClick={() => onQuickLogin("admin")}
              className="flex items-center justify-between rounded-xl border border-border bg-secondary px-4 py-3 text-left transition-all duration-200 hover:border-ring hover:bg-secondary/70 active:scale-[0.99]"
            >
              <span className="flex items-center gap-3">
                <span className="flex size-9 items-center justify-center rounded-full bg-foreground text-xs font-bold text-background">
                  <Shield className="size-4" />
                </span>
                <span>
                  <span className="block text-sm font-semibold text-foreground">Login as Admin</span>
                  <span className="block text-xs text-muted-foreground">System control panel</span>
                </span>
              </span>
              <Shield className="size-4 text-muted-foreground" />
            </button>
          </div>
        </div>

        <p className="mt-6 text-center text-xs text-muted-foreground">
          This is a front-end demo. No real accounts or money are involved.
        </p>
      </div>
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/*  Shared top navigation                                                     */
/* -------------------------------------------------------------------------- */

function TopBar({
  user,
  onLogout,
  badge,
  theme,
  toggleTheme,
}: {
  user: Account
  onLogout: () => void
  badge?: React.ReactNode
  theme: Theme
  toggleTheme: () => void
}) {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-card/80 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <div className="flex items-center gap-2.5">
          <span className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Landmark className="size-5" />
          </span>
          <span className="hidden text-base font-bold tracking-tight text-foreground sm:block">
            SJSJS Bank
          </span>
          {badge}
        </div>
        <div className="flex items-center gap-3">
          <ThemeToggle theme={theme} toggle={toggleTheme} />
          <div className="hidden text-right sm:block">
            <p className="text-sm font-semibold leading-tight text-foreground">{user.name}</p>
            <p className="font-mono text-xs text-muted-foreground">{user.accountNumber}</p>
          </div>
          <span
            className="flex size-9 items-center justify-center rounded-full text-xs font-bold text-primary-foreground"
            style={{ backgroundColor: user.color }}
          >
            {user.role === "admin" ? <Shield className="size-4" /> : initials(user.name)}
          </span>
          <button
            onClick={onLogout}
            className="flex items-center gap-1.5 rounded-xl border border-border bg-secondary px-3 py-2 text-sm font-medium text-foreground transition-all duration-200 hover:bg-secondary/70 active:scale-95"
          >
            <LogOut className="size-4" />
            <span className="hidden sm:inline">Logout</span>
          </button>
        </div>
      </div>
    </header>
  )
}

/* -------------------------------------------------------------------------- */
/*  View: Client dashboard                                                    */
/* -------------------------------------------------------------------------- */

function DashboardView({
  user,
  users,
  transactions,
  onTransfer,
  onLogout,
  theme,
  toggleTheme,
}: {
  user: Account
  users: Account[]
  transactions: Transaction[]
  onTransfer: (toId: string, amount: number) => void
  onLogout: () => void
  theme: Theme
  toggleTheme: () => void
}) {
  const [showBalance, setShowBalance] = useState(true)
  const [recipient, setRecipient] = useState("")
  const [amount, setAmount] = useState("")

  const otherUsers = users.filter((u) => u.id !== user.id && u.role === "client")
  const myTransactions = transactions
    .filter((t) => t.fromId === user.id || t.toId === user.id)
    .sort((a, b) => b.timestamp - a.timestamp)

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    onTransfer(recipient, Number(amount))
    setAmount("")
    setRecipient("")
  }

  const numericAmount = Number(amount)
  const invalid =
    !recipient ||
    !amount ||
    numericAmount <= 0 ||
    numericAmount > user.balance ||
    Number.isNaN(numericAmount)

  return (
    <div className="min-h-screen bg-background">
      <TopBar user={user} onLogout={onLogout} theme={theme} toggleTheme={toggleTheme} />
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight text-foreground text-balance">
            Hello, {user.name.split(" ")[0]}!
          </h1>
          <p className="text-sm text-muted-foreground">Here&apos;s an overview of your account.</p>
        </div>

        <div className="grid gap-6 lg:grid-cols-5">
          {/* Left column: balance card + send money */}
          <div className="flex flex-col gap-6 lg:col-span-3">
            {/* Balance / credit card */}
            <div className="relative overflow-hidden rounded-3xl bg-primary p-6 text-primary-foreground shadow-lg sm:p-8">
              <div className="absolute -right-10 -top-10 size-40 rounded-full bg-primary-foreground/10" />
              <div className="absolute -bottom-16 -left-8 size-48 rounded-full bg-primary-foreground/5" />
              <div className="relative flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-primary-foreground/70">Available balance</p>
                  <div className="mt-2 flex items-center gap-3">
                    <span className="font-mono text-4xl font-bold tracking-tight sm:text-5xl">
                      {showBalance ? currency(user.balance) : "••••••"}
                    </span>
                    <button
                      onClick={() => setShowBalance((s) => !s)}
                      aria-label={showBalance ? "Hide balance" : "Show balance"}
                      className="flex size-8 items-center justify-center rounded-full bg-primary-foreground/10 transition-colors hover:bg-primary-foreground/20"
                    >
                      {showBalance ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                    </button>
                  </div>
                </div>
                <CreditCard className="size-8 text-primary-foreground/60" />
              </div>
              <div className="relative mt-10 flex items-end justify-between">
                <div>
                  <p className="text-xs uppercase tracking-widest text-primary-foreground/50">Card holder</p>
                  <p className="mt-0.5 text-sm font-semibold">{user.name}</p>
                </div>
                <p className="font-mono text-sm tracking-widest text-primary-foreground/80">
                  {user.accountNumber}
                </p>
              </div>
            </div>

            {/* Send money */}
            <div className="rounded-3xl border border-border bg-card p-6">
              <div className="mb-4 flex items-center gap-2">
                <span className="flex size-9 items-center justify-center rounded-xl bg-accent/15 text-accent">
                  <Send className="size-4" />
                </span>
                <div>
                  <h2 className="text-base font-semibold text-card-foreground">Send money</h2>
                  <p className="text-xs text-muted-foreground">Transfer funds instantly to another account.</p>
                </div>
              </div>
              <form onSubmit={submit} className="flex flex-col gap-4">
                <label className="block">
                  <span className="mb-1.5 block text-sm font-medium text-foreground">Recipient</span>
                  <select
                    value={recipient}
                    onChange={(e) => setRecipient(e.target.value)}
                    className="h-11 w-full rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none transition-all duration-200 focus:border-ring focus:ring-2 focus:ring-ring/30"
                  >
                    <option value="">Select an account…</option>
                    {otherUsers.map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.name} — {u.accountNumber}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="block">
                  <span className="mb-1.5 block text-sm font-medium text-foreground">Amount</span>
                  <span className="relative block">
                    <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center font-medium text-muted-foreground">
                      $
                    </span>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      inputMode="decimal"
                      placeholder="0.00"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      className="h-11 w-full rounded-xl border border-input bg-background pl-8 pr-3 text-sm text-foreground outline-none transition-all duration-200 placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/30"
                    />
                  </span>
                  {amount && numericAmount > user.balance && (
                    <span className="mt-1.5 block text-xs font-medium text-destructive">
                      Amount exceeds your available balance.
                    </span>
                  )}
                </label>

                <button
                  type="submit"
                  disabled={invalid}
                  className="flex h-11 items-center justify-center gap-2 rounded-xl bg-accent text-sm font-semibold text-accent-foreground shadow-sm transition-all duration-200 hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <Send className="size-4" />
                  Transfer {amount && numericAmount > 0 ? currency(numericAmount) : "funds"}
                </button>
              </form>
            </div>
          </div>

          {/* Right column: transactions */}
          <div className="lg:col-span-2">
            <div className="rounded-3xl border border-border bg-card p-6">
              <div className="mb-4 flex items-center gap-2">
                <span className="flex size-9 items-center justify-center rounded-xl bg-secondary text-foreground">
                  <Receipt className="size-4" />
                </span>
                <h2 className="text-base font-semibold text-card-foreground">Recent transactions</h2>
              </div>

              {myTransactions.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">No transactions yet.</p>
              ) : (
                <ul className="flex flex-col divide-y divide-border">
                  {myTransactions.map((t) => {
                    const incoming = t.toId === user.id
                    return (
                      <li key={t.id} className="flex items-center gap-3 py-3">
                        <span
                          className={`flex size-9 shrink-0 items-center justify-center rounded-full ${incoming ? "bg-accent/15 text-accent" : "bg-secondary text-foreground"
                            }`}
                        >
                          {incoming ? (
                            <ArrowDownLeft className="size-4" />
                          ) : (
                            <ArrowUpRight className="size-4" />
                          )}
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium text-card-foreground">
                            {incoming ? t.fromName : t.toName}
                          </p>
                          <p className="text-xs text-muted-foreground">{formatDate(t.timestamp)}</p>
                        </div>
                        <span
                          className={`shrink-0 font-mono text-sm font-semibold ${incoming ? "text-accent" : "text-foreground"
                            }`}
                        >
                          {incoming ? "+" : "-"}
                          {currency(t.amount)}
                        </span>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/*  View: Admin dashboard                                                     */
/* -------------------------------------------------------------------------- */

function Metric({
  icon,
  label,
  value,
  accent,
}: {
  icon: React.ReactNode
  label: string
  value: string
  accent?: boolean
}) {
  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="flex items-center justify-between">
        <span
          className={`flex size-10 items-center justify-center rounded-xl ${accent ? "bg-accent/15 text-accent" : "bg-secondary text-foreground"
            }`}
        >
          {icon}
        </span>
      </div>
      <p className="mt-4 text-2xl font-bold tracking-tight text-card-foreground">{value}</p>
      <p className="text-sm text-muted-foreground">{label}</p>
    </div>
  )
}

function AdminView({
  admin,
  users,
  transactions,
  onLogout,
  theme,
  toggleTheme,
}: {
  admin: Account
  users: Account[]
  transactions: Transaction[]
  onLogout: () => void
  theme: Theme
  toggleTheme: () => void
}) {
  const [query, setQuery] = useState("")

  const clients = users.filter((u) => u.role === "client")
  const totalBalance = clients.reduce((sum, u) => sum + u.balance, 0)

  const sorted = [...transactions].sort((a, b) => b.timestamp - a.timestamp)
  const filtered = sorted.filter((t) => {
    const q = query.toLowerCase()
    return (
      t.id.toLowerCase().includes(q) ||
      t.fromName.toLowerCase().includes(q) ||
      t.toName.toLowerCase().includes(q)
    )
  })

  return (
    <div className="min-h-screen bg-background">
      <TopBar
        user={admin}
        onLogout={onLogout}
        theme={theme}
        toggleTheme={toggleTheme}
        badge={
          <span className="ml-1 flex items-center gap-1 rounded-full bg-foreground px-2.5 py-1 text-xs font-semibold text-background">
            <Shield className="size-3" />
            Admin
          </span>
        }
      />
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Admin Panel</h1>
          <p className="text-sm text-muted-foreground">System-wide overview and ledger.</p>
        </div>

        {/* Metrics */}
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
          <Metric
            icon={<Wallet className="size-5" />}
            label="Total system balance"
            value={currency(totalBalance)}
            accent
          />
          <Metric
            icon={<TrendingUp className="size-5" />}
            label="Transactions processed"
            value={String(transactions.length)}
          />
          <Metric
            icon={<Users className="size-5" />}
            label="Active users"
            value={String(clients.length)}
          />
        </div>

        {/* Ledger */}
        <div className="mb-6 rounded-3xl border border-border bg-card p-6">
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
              <span className="flex size-9 items-center justify-center rounded-xl bg-secondary text-foreground">
                <Receipt className="size-4" />
              </span>
              <h2 className="text-base font-semibold text-card-foreground">Master transaction ledger</h2>
            </div>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search by ID or name…"
                className="h-10 w-full rounded-xl border border-input bg-background pl-9 pr-3 text-sm text-foreground outline-none transition-all duration-200 placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/30 sm:w-64"
              />
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="py-3 pr-4 font-medium">Transaction ID</th>
                  <th className="py-3 pr-4 font-medium">Sender</th>
                  <th className="py-3 pr-4 font-medium">Receiver</th>
                  <th className="py-3 pr-4 text-right font-medium">Amount</th>
                  <th className="py-3 text-right font-medium">Date / time</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((t) => (
                  <tr
                    key={t.id}
                    className="border-b border-border/60 transition-colors last:border-0 hover:bg-secondary/50"
                  >
                    <td className="py-3 pr-4 font-mono text-xs text-muted-foreground">{t.id}</td>
                    <td className="py-3 pr-4 font-medium text-card-foreground">{t.fromName}</td>
                    <td className="py-3 pr-4 font-medium text-card-foreground">{t.toName}</td>
                    <td className="py-3 pr-4 text-right font-mono font-semibold text-accent">
                      {currency(t.amount)}
                    </td>
                    <td className="py-3 text-right text-xs text-muted-foreground">
                      {formatDate(t.timestamp)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {filtered.length === 0 && (
              <p className="py-8 text-center text-sm text-muted-foreground">No matching transactions.</p>
            )}
          </div>
        </div>

        {/* User management */}
        <div className="rounded-3xl border border-border bg-card p-6">
          <div className="mb-4 flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-xl bg-secondary text-foreground">
              <Users className="size-4" />
            </span>
            <h2 className="text-base font-semibold text-card-foreground">User management</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[480px] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="py-3 pr-4 font-medium">User</th>
                  <th className="py-3 pr-4 font-medium">Account number</th>
                  <th className="py-3 text-right font-medium">Balance</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((u) => (
                  <tr
                    key={u.id}
                    className="border-b border-border/60 transition-colors last:border-0 hover:bg-secondary/50"
                  >
                    <td className="py-3 pr-4">
                      <div className="flex items-center gap-3">
                        <span
                          className="flex size-8 items-center justify-center rounded-full text-xs font-bold text-primary-foreground"
                          style={{ backgroundColor: u.color }}
                        >
                          {initials(u.name)}
                        </span>
                        <div>
                          <p className="font-medium text-card-foreground">{u.name}</p>
                          <p className="text-xs text-muted-foreground">{u.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-3 pr-4 font-mono text-xs text-muted-foreground">{u.accountNumber}</td>
                    <td className="py-3 text-right font-mono font-semibold text-card-foreground">
                      {currency(u.balance)}
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

/* -------------------------------------------------------------------------- */
/*  Root app                                                                  */
/* -------------------------------------------------------------------------- */

export default function BankingApp() {
  const [users, setUsers] = useState<Account[]>(INITIAL_USERS)
  const [transactions, setTransactions] = useState<Transaction[]>(INITIAL_TRANSACTIONS)
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)
  const [view, setView] = useState<View>("login")
  const [toasts, setToasts] = useState<Toast[]>([])
  const { theme, toggle: toggleTheme } = useTheme()

  const currentUser = useMemo(
    () => users.find((u) => u.id === currentUserId) ?? null,
    [users, currentUserId],
  )

  const pushToast = (type: Toast["type"], message: string) => {
    const id = Date.now() + Math.random()
    setToasts((prev) => [...prev, { id, type, message }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4000)
  }
  const dismissToast = (id: number) => setToasts((prev) => prev.filter((t) => t.id !== id))

  const enter = (user: Account) => {
    setCurrentUserId(user.id)
    setView(user.role === "admin" ? "admin" : "dashboard")
    pushToast("success", `Signed in as ${user.name}.`)
  }

  const handleLogin = (email: string) => {
    const match = users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase())
    if (!match) {
      pushToast("error", "No account found. Try a demo quick-login below.")
      return
    }
    enter(match)
  }

  const handleQuickLogin = (id: string) => {
    const user = users.find((u) => u.id === id)
    if (user) enter(user)
  }

  const handleLogout = () => {
    setCurrentUserId(null)
    setView("login")
  }

  const handleTransfer = (toId: string, amount: number) => {
    if (!currentUser) return
    const recipient = users.find((u) => u.id === toId)
    if (!recipient) {
      pushToast("error", "Please choose a valid recipient.")
      return
    }
    if (!(amount > 0)) {
      pushToast("error", "Enter an amount greater than zero.")
      return
    }
    if (amount > currentUser.balance) {
      pushToast("error", "Insufficient funds for this transfer.")
      return
    }

    setUsers((prev) =>
      prev.map((u) => {
        if (u.id === currentUser.id) return { ...u, balance: u.balance - amount }
        if (u.id === toId) return { ...u, balance: u.balance + amount }
        return u
      }),
    )
    setTransactions((prev) => [
      {
        id: `TXN-${100235 + prev.length}`,
        fromId: currentUser.id,
        toId,
        fromName: currentUser.name,
        toName: recipient.name,
        amount,
        timestamp: Date.now(),
      },
      ...prev,
    ])
    pushToast("success", `${currency(amount)} sent to ${recipient.name}.`)
  }

  return (
    <>
      <ToastStack toasts={toasts} dismiss={dismissToast} />
      {view === "login" || !currentUser ? (
        <LoginView
          onLogin={handleLogin}
          onQuickLogin={handleQuickLogin}
          theme={theme}
          toggleTheme={toggleTheme}
        />
      ) : view === "admin" ? (
        <AdminView
          admin={currentUser}
          users={users}
          transactions={transactions}
          onLogout={handleLogout}
          theme={theme}
          toggleTheme={toggleTheme}
        />
      ) : (
        <DashboardView
          user={currentUser}
          users={users}
          transactions={transactions}
          onTransfer={handleTransfer}
          onLogout={handleLogout}
          theme={theme}
          toggleTheme={toggleTheme}
        />
      )}
    </>
  )
}
