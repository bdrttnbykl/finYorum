import { useEffect, useMemo, useState } from 'react'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Filler,
  type Chart,
} from 'chart.js'
import { Line } from 'react-chartjs-2'
import './App.css'

const hoverLinePlugin = {
  id: 'hoverLine',
  afterDatasetsDraw(chart: Chart<'line'>) {
    const active = chart.tooltip?.getActiveElements()
    if (!active?.length) {
      return
    }

    const { ctx, chartArea } = chart
    const x = active[0].element.x
    ctx.save()
    ctx.beginPath()
    ctx.setLineDash([5, 5])
    ctx.moveTo(x, chartArea.top)
    ctx.lineTo(x, chartArea.bottom)
    ctx.lineWidth = 1
    ctx.strokeStyle = '#1fbd6a'
    ctx.stroke()
    ctx.restore()
  },
}

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Filler, hoverLinePlugin)

type QuoteResponse = {
  symbol: string
  currentPrice: number
  change: number
  percentChange: number
  timestamp: string
  source: string
}

type RiskResponse = {
  symbol: string
  volatility: number
  sharpeRatio: number
  riskLevel: string
}

type AiAnalysisResponse = {
  symbol: string
  recommendation: string
  summary: string
}

type MarketChartResponse = {
  symbol: string
  days: number
  source: string
  prices: Array<{
    timestamp: string
    price: number
  }>
}

type CryptoSearchResult = {
  id: string
  symbol: string
  name: string
  thumb: string
  marketCapRank: number | null
}

type CryptoMarketStats = {
  id: string
  symbol: string
  name: string
  image: string
  marketCapRank: number | null
  currentPrice: number
  priceChange24h: number
  priceChangePercentage24h: number
  low24h: number
  high24h: number
  marketCap: number
  fullyDilutedValuation: number
  totalVolume: number
  circulatingSupply: number
  totalSupply: number
  maxSupply: number
  source: string
}

type CryptoDashboardResponse = {
  quote: QuoteResponse
  market: CryptoMarketStats
  risk: RiskResponse
  analysis: AiAnalysisResponse
  chart: MarketChartResponse
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
const quickAssets = ['BTC', 'ETH', 'SOL', 'BNB', 'XRP', 'ADA', 'AVAX', 'DOGE', 'TRX', 'TON', 'LINK', 'SUI', 'CRV']

function App() {
  const [symbol, setSymbol] = useState('BTC')
  const [activeSymbol, setActiveSymbol] = useState('BTC')
  const [quote, setQuote] = useState<QuoteResponse | null>(null)
  const [market, setMarket] = useState<CryptoMarketStats | null>(null)
  const [risk, setRisk] = useState<RiskResponse | null>(null)
  const [analysis, setAnalysis] = useState<AiAnalysisResponse | null>(null)
  const [chart, setChart] = useState<MarketChartResponse | null>(null)
  const [suggestions, setSuggestions] = useState<CryptoSearchResult[]>([])
  const [searchFocused, setSearchFocused] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    void loadDashboard(activeSymbol)
  }, [activeSymbol])

  useEffect(() => {
    const query = symbol.trim()
    if (!searchFocused || loading || query.length < 2) {
      setSuggestions([])
      return
    }

    const timeout = window.setTimeout(async () => {
      try {
        const response = await fetch(`${apiBaseUrl}/api/crypto/search?query=${encodeURIComponent(query)}`)
        if (!response.ok) {
          return
        }
        setSuggestions(await response.json())
      } catch {
        setSuggestions([])
      }
    }, 250)

    return () => window.clearTimeout(timeout)
  }, [symbol, searchFocused, loading])

  async function loadDashboard(nextSymbol: string) {
    const normalized = nextSymbol.trim().toUpperCase()
    if (!normalized) {
      return
    }

    setLoading(true)
    setError('')
    setSuggestions([])

    try {
      const dashboardResponse = await fetch(`${apiBaseUrl}/api/crypto/${normalized}/dashboard?days=30`)

      if (!dashboardResponse.ok) {
        throw new Error('API response failed')
      }

      const dashboard: CryptoDashboardResponse = await dashboardResponse.json()
      setQuote(dashboard.quote)
      setMarket(dashboard.market)
      setRisk(dashboard.risk)
      setAnalysis(dashboard.analysis)
      setChart(dashboard.chart)
    } catch {
      setQuote(null)
      setMarket(null)
      setRisk(null)
      setAnalysis(null)
      setChart(null)
      setError('Bu varlik icin CoinGecko verisi alinamadi. Sembol yerine coin adini deneyebilirsin.')
    } finally {
      setLoading(false)
    }
  }

  function submitSymbol(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSuggestions([])
    setActiveSymbol(symbol)
  }

  const chartData = useMemo(() => {
    const points = chart?.prices ?? []
    const sampledPoints = points.length > 80
      ? points.filter((_, index) => index % Math.ceil(points.length / 80) === 0)
      : points

    return {
      labels: sampledPoints.map((point) => point.timestamp),
      datasets: [
        {
          data: sampledPoints.map((point) => point.price),
          borderColor: '#1f7a5a',
          backgroundColor: 'rgba(31, 122, 90, 0.14)',
          fill: true,
          tension: 0.36,
          pointRadius: 0,
          pointHoverRadius: 6,
          pointHoverBackgroundColor: '#1fbd6a',
          pointHoverBorderColor: '#ffffff',
          pointHoverBorderWidth: 3,
        },
      ],
    }
  }, [chart])

  const analysisText = analysis?.summary?.trim()
    || (loading
      ? 'Analiz hazirlaniyor...'
      : error || 'Bir kripto varlik analiz edildiginde yorum burada gorunecek.')

  const formatUsd = (value?: number | null) =>
    value && value > 0
      ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: value < 1 ? 6 : 2 }).format(value)
      : '-'

  const formatNumber = (value?: number | null) =>
    value && value > 0 ? new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value) : '-'

  return (
    <main className="dashboard">
      <header className="topbar">
        <div>
          <p className="eyebrow">FinYorum</p>
          <h1>AI destekli kripto analiz paneli</h1>
        </div>

        <form className="symbol-form" onSubmit={submitSymbol}>
          <div className="search-box">
            <input
              aria-label="Kripto varlik"
              value={symbol}
              onChange={(event) => setSymbol(event.target.value)}
              onFocus={() => setSearchFocused(true)}
              onBlur={() =>
                window.setTimeout(() => {
                  setSearchFocused(false)
                  setSuggestions([])
                }, 120)
              }
              placeholder="BTC, CRV, Curve"
            />
            {suggestions.length > 0 ? (
              <div className="suggestions">
                {suggestions.map((result) => (
                  <button
                    key={result.id}
                    type="button"
                    onClick={() => {
                      setSymbol(result.symbol)
                      setActiveSymbol(result.symbol)
                      setSuggestions([])
                    }}
                  >
                    {result.thumb ? <img src={result.thumb} alt="" /> : null}
                    <span>{result.symbol}</span>
                    <small>{result.name}</small>
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <button type="submit" disabled={loading}>
            Analiz et
          </button>
        </form>
      </header>

      <div className="quick-assets" aria-label="Hizli kripto secimi">
        {quickAssets.map((asset) => (
          <button
            key={asset}
            type="button"
            className={asset === activeSymbol.toUpperCase() ? 'active' : ''}
            onClick={() => {
              setSymbol(asset)
              setActiveSymbol(asset)
            }}
          >
            {asset}
          </button>
        ))}
      </div>

      {error ? <div className="alert">{error}</div> : null}

      <section className="metrics">
        <article className="metric">
          <span>Fiyat</span>
          <strong>{quote ? `$${Number(quote.currentPrice).toFixed(2)}` : '-'}</strong>
          <small className={Number(quote?.change ?? 0) >= 0 ? 'positive' : 'negative'}>
            {quote ? `${Number(quote.change).toFixed(2)} (${Number(quote.percentChange).toFixed(2)}%)` : 'Bekleniyor'}
          </small>
        </article>

        <article className="metric">
          <span>Risk</span>
          <strong>{risk?.riskLevel ?? '-'}</strong>
          <small>Volatilite {risk?.volatility ?? '-'}</small>
        </article>

        <article className="metric">
          <span>Sharpe</span>
          <strong>{risk?.sharpeRatio ?? '-'}</strong>
          <small>Risk ayarli getiri</small>
        </article>

        <article className="metric">
          <span>Sinyal</span>
          <strong>{analysis?.recommendation ?? '-'}</strong>
          <small>{quote?.source ? `Kaynak: ${quote.source}` : 'Hazirlaniyor'}</small>
        </article>
      </section>

      <section className="workspace">
        <article className="panel chart-panel">
          <div className="panel-header">
            <div>
              <h2>{activeSymbol.toUpperCase()} piyasa hareketi</h2>
              <p>Son 30 gunluk gercek fiyat gecmisi</p>
            </div>
            <span>{loading ? 'Yukleniyor' : 'Canli'}</span>
          </div>
          <div className="chart-wrap">
            <Line
              data={chartData}
              options={{
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                  legend: { display: false },
                  tooltip: {
                    displayColors: false,
                    backgroundColor: '#ffffff',
                    titleColor: '#26342f',
                    bodyColor: '#26342f',
                    borderColor: '#dce4df',
                    borderWidth: 1,
                    padding: 12,
                    callbacks: {
                      title: (items) => {
                        const raw = items[0]?.label
                        if (!raw) {
                          return ''
                        }
                        return new Intl.DateTimeFormat('tr-TR', {
                          day: '2-digit',
                          month: 'short',
                          year: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        }).format(new Date(raw))
                      },
                      label: (item) => `Fiyat: ${formatUsd(Number(item.parsed.y))}`,
                    },
                  },
                },
                scales: {
                  x: {
                    grid: { display: false },
                    ticks: {
                      maxTicksLimit: 8,
                      callback: (_value, index) => {
                        const raw = chartData.labels[index]
                        return raw
                          ? new Intl.DateTimeFormat('tr-TR', { day: '2-digit', month: 'short' }).format(new Date(raw))
                          : ''
                      },
                    },
                  },
                  y: { grid: { color: '#e6ece8' } },
                },
              }}
            />
          </div>
        </article>

        <aside className="panel ai-panel">
          <div className="panel-header">
            <div>
              <h2>AI yorumu</h2>
              <p>Kripto sinyali ve risk ozeti</p>
            </div>
          </div>
          <p className="analysis-text">
            {analysisText}
          </p>
        </aside>
      </section>

      <section className="panel market-panel">
        <div className="panel-header">
          <div>
            <h2>Piyasa bilgileri</h2>
            <p>{market ? `${market.name} ${market.marketCapRank ? `#${market.marketCapRank}` : ''}` : 'CoinGecko market verileri'}</p>
          </div>
        </div>
        <div className="market-grid">
          <div><span>24s dusuk</span><strong>{formatUsd(market?.low24h)}</strong></div>
          <div><span>24s yuksek</span><strong>{formatUsd(market?.high24h)}</strong></div>
          <div><span>Piyasa degeri</span><strong>{formatUsd(market?.marketCap)}</strong></div>
          <div><span>Tam seyreltilmis deger</span><strong>{formatUsd(market?.fullyDilutedValuation)}</strong></div>
          <div><span>24s hacim</span><strong>{formatUsd(market?.totalVolume)}</strong></div>
          <div><span>Dolasim arzi</span><strong>{formatNumber(market?.circulatingSupply)}</strong></div>
          <div><span>Toplam arz</span><strong>{formatNumber(market?.totalSupply)}</strong></div>
          <div><span>Maksimum arz</span><strong>{formatNumber(market?.maxSupply)}</strong></div>
        </div>
      </section>
    </main>
  )
}

export default App
