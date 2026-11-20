import { useCallback, useEffect, useMemo, useState, type ComponentType, type Dispatch, type FormEvent, type ReactNode, type SetStateAction } from 'react'
import {
  Activity, ArrowDown, ArrowLeft, ArrowUp, BookOpen, Check, ChevronDown, CircleHelp, Coins,
  ClipboardList, FileImage, FilePlus2, Files, Filter, Flower2, Home, LogOut, MoreHorizontal, Printer, Receipt, TrendingUp,
  Package, Plus, Search, Settings2, ShieldCheck, Sparkles, Upload, X,
} from 'lucide-react'

type Credentials = { username: string; password: string }
type Term = { id: string; term_type: 'AGE' | 'TASK_TYPE' | 'TOPIC' | 'SKILL'; name: string; slug: string; sort_order?: number; active?: boolean; default_description?: string; default_instructions?: string }
type Page = {
  id?: string; page_type: 'MAIN' | 'CUTOUT'; title?: string; sort_order: number; page_count: number
  paper_size: string; orientation: string; color_mode: string; notes?: string
  preview_asset_id?: string; preview_name?: string; preview_media_type?: string; print_asset_id?: string; print_name?: string; print_media_type?: string
}
type Worksheet = {
  id: string; title: string; slug: string; summary?: string; description?: string; instructions?: string
  status: string; paper_size: string; orientation: string; color_mode: string; usage_type: string
  safety_notes?: string; preview_asset_id?: string; pages?: Page[]; terms?: Term[]; totalPages?: number; total_pages?: number
  saleProducts?: WorksheetSaleProduct[]
}
type WorksheetSaleProduct = {id:string;worksheet_id?:string;delivery_type:'DIGITAL'|'PHYSICAL';variant_name:string;list_price:number;material_cost:number;print_sides:number;printing_cost:number;manufacturing_cost:number;units_sold:number;revenue:number;sold_material_cost:number;materials?:ProductMaterial[]}
type ActivityItem = { id: string; title: string; summary?: string; status: string; duration_minutes?: number }
type ActivityForm = { id: string; title: string; summary: string; durationMinutes: string; instructions: string; materials:string; preparation:string; easyVariant:string; challengeVariant:string; safetyNotes:string; locationType:string; status: string; termIds:string[] }
type BundleItem = { id:string; title:string; description?:string; bundle_type:string; status:string; worksheet_count?:number; worksheetIds?:string[] }
type BundleForm = { id:string; title:string; slug:string; description:string; bundleType:string; status:string; worksheetIds:string[] }
type Dashboard = { worksheets: number; activities: number; drafts: number; recentWorksheets: Worksheet[] }
type Section = 'overview' | 'worksheets' | 'activities' | 'bundles' | 'finance' | 'sales' | 'taxonomy'

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'
const MAX_UPLOAD_FILE_BYTES = 20 * 1024 * 1024
const MAX_UPLOAD_BATCH_BYTES = 24 * 1024 * 1024
const localDateInputValue = () => { const d=new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const saleDateInputValue = (value:string) => { const match=String(value??'').match(/^(\d{4}-\d{2}-\d{2})/); if(match)return match[1]; const d=new Date(value); return Number.isNaN(d.getTime())?localDateInputValue():`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const formatSaleDate = (value:string) => { const date=saleDateInputValue(value); const [year,month,day]=date.split('-').map(Number); return new Intl.DateTimeFormat('hu-HU').format(new Date(year,month-1,day)) }
const todayLabel = new Intl.DateTimeFormat('hu-HU', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date()).toLocaleUpperCase('hu')
const blankPage = (pageType: Page['page_type'], sort_order: number): Page => ({ page_type: pageType, title: pageType === 'MAIN' ? 'Alap feladatlap' : '', sort_order, page_count: 1, paper_size: 'A4', orientation: 'PORTRAIT', color_mode: 'COLOR' })
const blankWorksheet = (): Worksheet => ({ id: '', title: '', slug: '', status: 'DRAFT', paper_size: 'A4', orientation: 'PORTRAIT', color_mode: 'COLOR', usage_type: 'DIGITAL', pages: [blankPage('MAIN', 0)], terms: [] })

async function api<T>(credentials: Credentials, path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const raw = new TextEncoder().encode(`${credentials.username}:${credentials.password}`)
  let binary = ''
  raw.forEach((byte) => { binary += String.fromCharCode(byte) })
  headers.set('Authorization', `Basic ${btoa(binary)}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${API}${path}`, { ...init, headers })
  if (!response.ok) {
    let message = `Hiba (${response.status})`
    try { message = (await response.json()).error ?? message } catch { /* non-JSON response */ }
    if (response.status === 401) throw new Error('A belépési adatok nem megfelelőek.')
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export default function App() {
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [loginError, setLoginError] = useState('')
  const [section, setSection] = useState<Section>('worksheets')
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [worksheets, setWorksheets] = useState<Worksheet[]>([])
  const [activities, setActivities] = useState<ActivityItem[]>([])
  const [bundles, setBundles] = useState<BundleItem[]>([])
  const [terms, setTerms] = useState<Term[]>([])
  const [selectedWorksheet, setSelectedWorksheet] = useState<Worksheet | null>(null)
  const [financeTarget,setFinanceTarget]=useState<{worksheetId:string;productId?:string}|null>(null)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [taxonomyFilters, setTaxonomyFilters] = useState<Record<Term['term_type'], string[]>>({ AGE: [], TASK_TYPE: [], TOPIC: [], SKILL: [] })
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [form, setForm] = useState<Worksheet>(blankWorksheet())
  const [activityForm, setActivityForm] = useState<ActivityForm>({ id: '', title: '', summary: '', durationMinutes: '', instructions: '', materials:'',preparation:'',easyVariant:'',challengeVariant:'',safetyNotes:'',locationType:'ANY',status: 'DRAFT', termIds:[] })
  const [bundleForm, setBundleForm] = useState<BundleForm>({ id:'',title:'',slug:'',description:'',bundleType:'DIGITAL',status:'DRAFT',worksheetIds:[] })
  const [busy, setBusy] = useState(false)
  const [dataLoadState, setDataLoadState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [toast, setToast] = useState('')
  const [error, setError] = useState('')

  const loadDashboard = useCallback(async (auth: Credentials) => {
    const [data, worksheetData, activityData, taxonomy, bundleData] = await Promise.all([
      api<Dashboard>(auth, '/dashboard'), api<{ items: Worksheet[] }>(auth, '/worksheets?size=100'),
      api<{ items: ActivityItem[] }>(auth, '/activities?size=100'), api<Term[]>(auth, '/taxonomy?includeInactive=true'), api<BundleItem[]>(auth,'/bundles'),
    ])
    setDashboard(data); setWorksheets(worksheetData.items); setActivities(activityData.items); setTerms(taxonomy); setBundles(bundleData)
    setError('')
  }, [])

  useEffect(() => {
    if (!credentials) return
    let active = true
    setDataLoadState('loading')
    loadDashboard(credentials)
      .then(() => { if (active) setDataLoadState('ready') })
      .catch((e: Error) => {
        if (!active) return
        setDataLoadState('error')
        setError(e.message)
        if (e.message.includes('belépési')) setCredentials(null)
      })
    return () => { active = false }
  }, [credentials, loadDashboard])

  async function retryInitialLoad() {
    if (!credentials) return
    setDataLoadState('loading')
    try {
      await loadDashboard(credentials)
      setDataLoadState('ready')
    } catch (e) {
      setDataLoadState('error')
      setError((e as Error).message)
    }
  }

  const notify = (message: string) => { setToast(message); window.setTimeout(() => setToast(''), 3000) }
  const visibleWorksheets = useMemo(() => worksheets.filter((item) => {
    const q = query.trim().toLocaleLowerCase('hu')
    const textMatches = !q || `${item.title} ${item.summary ?? ''}`.toLocaleLowerCase('hu').includes(q)
    const statusMatches = !statusFilter || item.status === statusFilter
    const taxonomyMatches = (Object.entries(taxonomyFilters) as [Term['term_type'], string[]][]).every(([type, ids]) => !ids.length || item.terms?.some((term) => term.term_type === type && ids.includes(term.id)))
    return textMatches && statusMatches && taxonomyMatches
  }), [worksheets, query, statusFilter, taxonomyFilters])

  async function login(event: FormEvent) {
    event.preventDefault(); setLoginError('')
    const auth = { username, password }
    try { await api(auth, '/auth/me'); setCredentials(auth); setPassword('') }
    catch (e) { setLoginError((e as Error).message) }
  }

  function openNewWorksheet() { setSelectedWorksheet(null); setEditingId(null); setEditorOpen(true); setForm(blankWorksheet()); setSection('worksheets') }
  function openWorksheetProduct(worksheetId:string,productId?:string){setFinanceTarget({worksheetId,productId});setSelectedWorksheet(null);setSection('finance')}
  async function openWorksheet(id: string) {
    if (!credentials) return
    try {
      const data = await api<Worksheet>(credentials, `/worksheets/${id}`)
      setSelectedWorksheet({ ...blankWorksheet(), ...data, pages: data.pages?.map((p) => ({ ...p, page_type: p.page_type })) ?? [blankPage('MAIN', 0)], terms: data.terms ?? [] })
      setEditingId(id); setEditorOpen(false); setSection('worksheets')
    } catch (e) { setError((e as Error).message) }
  }
  function editSelectedWorksheet() {
    if (!selectedWorksheet) return
    setForm({ ...blankWorksheet(), ...selectedWorksheet, pages: selectedWorksheet.pages?.map((page) => ({ ...page })) ?? [blankPage('MAIN', 0)], terms: selectedWorksheet.terms ?? [] })
    setEditingId(selectedWorksheet.id); setEditorOpen(true)
  }

  async function saveWorksheet(event: FormEvent) {
    event.preventDefault(); if (!credentials) return
    setBusy(true); setError('')
    try {
      const payload = {
        title: form.title, slug: form.slug, summary: form.summary, description: form.description,
        instructions: form.instructions, status: form.status, paperSize: form.paper_size,
        orientation: form.orientation, colorMode: form.color_mode, usageType: form.usage_type,
        safetyNotes: form.safety_notes, previewAssetId: form.preview_asset_id,
        termIds: form.terms?.map((t) => t.id),
        pages: form.pages?.map((p, i) => ({ pageType: p.page_type, title: p.title, sortOrder: i,
          pageCount: p.page_count, paperSize: p.paper_size, orientation: p.orientation,
          colorMode: p.color_mode, notes: p.notes, previewAssetId: p.preview_asset_id, printAssetId: p.print_asset_id }))
      }
      const saved = await api<Worksheet>(credentials, editingId ? `/worksheets/${editingId}` : '/worksheets', { method: editingId ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      await loadDashboard(credentials); setSelectedWorksheet(saved); notify(editingId ? 'Feladatlap elmentve' : 'Feladatlap létrehozva'); setEditingId(saved.id); setEditorOpen(false); setForm(blankWorksheet())
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }

  async function uploadFile(files: File[], pageIndex: number, target: 'preview_asset_id' | 'print_asset_id') {
    if (!credentials || !files.length) return
    const oversizedFile = files.find((file) => file.size > MAX_UPLOAD_FILE_BYTES)
    if (oversizedFile) {
      setError(`A „${oversizedFile.name}” fájl nagyobb a megengedett 20 MB-nál.`)
      return
    }
    const batchSize = files.reduce((sum, file) => sum + file.size, 0)
    if (batchSize > MAX_UPLOAD_BATCH_BYTES) {
      setError('A kijelölt fájlok együtt legfeljebb 24 MB méretűek lehetnek. Válassz ki kevesebb fájlt, vagy töltsd fel őket több részletben.')
      return
    }
    try {
      const data = new FormData()
      files.forEach((file) => data.append('files', file))
      const uploadedFiles = await api<{ id: string; originalName: string; mediaType: string }[]>(credentials, '/media/batch', { method: 'POST', body: data })
      setForm((old) => {
        if (pageIndex < 0) return { ...old, preview_asset_id: uploadedFiles[0].id }
        const pages = [...(old.pages ?? [])]
        const selected = pages[pageIndex]
        if (selected) pages[pageIndex] = { ...selected, [target]: uploadedFiles[0].id, ...(target === 'print_asset_id' ? { print_name: uploadedFiles[0].originalName, print_media_type: uploadedFiles[0].mediaType } : {}) }
        if (target === 'print_asset_id') uploadedFiles.slice(1).forEach((asset) => {
          const page = blankPage('CUTOUT', pages.length)
          const isImage = asset.mediaType.startsWith('image/')
          pages.push({ ...page, title: asset.originalName.replace(/\\.[^.]+$/, ''), print_asset_id: asset.id, print_name: asset.originalName, print_media_type: asset.mediaType, ...(isImage ? { preview_asset_id: asset.id } : {}) })
        })
        return { ...old, pages }
      })
      notify(uploadedFiles.length > 1 ? `${uploadedFiles.length} fájl feltöltve` : 'Fájl feltöltve')
    } catch (e) { setError((e as Error).message) }
  }

  async function saveActivity(event: FormEvent) {
    event.preventDefault(); if (!credentials) return
    setBusy(true)
    try {
      const { id, ...data } = activityForm
      await api(credentials, id ? `/activities/${id}` : '/activities', { method: id ? 'PUT' : 'POST', body: JSON.stringify({ ...data, durationMinutes: data.durationMinutes ? Number(data.durationMinutes) : null }) })
      await loadDashboard(credentials); setActivityForm({ id: '', title: '', summary: '', durationMinutes: '', instructions: '', materials:'',preparation:'',easyVariant:'',challengeVariant:'',safetyNotes:'',locationType:'ANY',status: 'DRAFT', termIds:[] }); notify('Játékötlet elmentve')
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }

  async function editActivity(id: string) {
    if (!credentials) return
    try {
      const item = await api<any>(credentials, `/activities/${id}`)
      setActivityForm({ id: item.id, title: item.title, summary: item.summary ?? '', durationMinutes: item.duration_minutes?.toString() ?? '', instructions: item.instructions ?? '', materials:item.materials??'',preparation:item.preparation??'',easyVariant:item.easy_variant??'',challengeVariant:item.challenge_variant??'',safetyNotes:item.safety_notes??'',locationType:item.location_type??'ANY',status: item.status, termIds:(item.terms??[]).map((term:Term)=>term.id) })
    } catch (e) { setError((e as Error).message) }
  }

  async function editBundle(id:string) {
    if(!credentials)return
    try { const data=await api<any>(credentials,`/bundles/${id}`);setBundleForm({id:data.id,title:data.title,slug:data.slug,description:data.description??'',bundleType:data.bundle_type,status:data.status,worksheetIds:(data.worksheets??[]).map((w:any)=>w.id)}) }
    catch(e){setError((e as Error).message)}
  }

  async function saveBundle(event:FormEvent) {
    event.preventDefault();if(!credentials)return;setBusy(true)
    try { const {id,...payload}=bundleForm;await api(credentials,id?`/bundles/${id}`:'/bundles',{method:id?'PUT':'POST',body:JSON.stringify(payload)});await loadDashboard(credentials);setBundleForm({id:'',title:'',slug:'',description:'',bundleType:'DIGITAL',status:'DRAFT',worksheetIds:[]});notify('Csomagterv elmentve') }
    catch(e){setError((e as Error).message)}finally{setBusy(false)}
  }

  if (!credentials) return <Login username={username} password={password} error={loginError} onUsername={setUsername} onPassword={setPassword} onSubmit={login} />

  const nav: { id: Section; label: string; icon: ComponentType<{ size?: number }> }[] = [
    { id: 'worksheets', label: 'Feladatlapok', icon: ClipboardList }, { id: 'overview', label: 'Áttekintő', icon: Home },
    { id: 'activities', label: 'Játékötletek', icon: Sparkles }, { id: 'bundles', label: 'Csomagtervek', icon: Package }, { id: 'finance', label: 'Pénzügy', icon: Coins }, { id: 'sales', label: 'Eladások', icon: Receipt }, { id: 'taxonomy', label: 'Kategóriák', icon: Settings2 },
  ]
  const navGroups = [
    { label: 'MŰHELY', items: nav.filter(({ id }) => ['overview','worksheets','activities','bundles'].includes(id)) },
    { label: 'PÉNZÜGY', items: nav.filter(({ id }) => id === 'finance' || id === 'sales') },
    { label: 'BEÁLLÍTÁSOK', items: nav.filter(({ id }) => id === 'taxonomy') },
  ]

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand-lockup"><div className="brand-mark"><Flower2 size={21} strokeWidth={1.8} /></div><div><strong>apróLéptek</strong><span>MŰHELY · ADMIN</span></div></div>
      <nav aria-label="Fő navigáció">{navGroups.map(({ label: groupLabel, items }) => <div className="nav-group" key={groupLabel}><div className="nav-group-label">{groupLabel}</div>{items.map(({ id, label, icon: Icon }) => {const active=section===id||(id==='finance'&&section==='sales');return <button key={id} title={label} aria-current={section === id ? 'page' : undefined} className={`nav-item ${active ? 'active' : ''} ${id==='sales'?'sub-nav-item':''}`} onClick={() => { setSection(id); setSelectedWorksheet(null); setEditingId(null); setEditorOpen(false); setError('') }}><Icon size={18} /><span>{label}</span>{id === 'worksheets' && <span className="nav-count">{worksheets.length}</span>}</button>})}</div>)}</nav>
      <div className="sidebar-bottom"><div className="profile"><div className="avatar">A</div><div className="profile-name"><strong>{credentials.username}</strong><span>Adminisztrátor</span></div><button className="icon-button" title="Kijelentkezés" onClick={() => { setCredentials(null); setDashboard(null) }}><LogOut size={17} /></button></div></div>
    </aside>
    <main className="main-area">
      <header className="topbar"><div className="breadcrumbs"><span>Műhely</span><span className="crumb-slash">/</span><strong>{nav.find((n) => n.id === section)?.label}</strong></div><div className="topbar-right"><span className="secure-label"><ShieldCheck size={15} /> Biztonságos admin</span><div className="topbar-avatar">A</div></div></header>
      <div className="page-content">
        {error && <div className="alert"><span>{error}</span><button onClick={() => setError('')}><X size={16} /></button></div>}
        {dataLoadState === 'loading' ? <div className="empty-state"><div className="empty-icon"><Files size={22}/></div><strong>Adatok betöltése…</strong><span>Mindjárt megjelenik a műhely tartalma.</span></div>
          : dataLoadState === 'error' ? <div className="empty-state"><div className="empty-icon"><CircleHelp size={22}/></div><strong>Nem sikerült betölteni az adatokat</strong><span>Ellenőrizd a kapcsolatot, majd próbáld újra.</span><button className="button secondary" onClick={retryInitialLoad}>Újrapróbálás</button></div>
          : <>
        {section === 'overview' && <Overview dashboard={dashboard} onNew={openNewWorksheet} onNavigate={setSection} onOpen={openWorksheet} />}
        {section === 'worksheets' && <>
          {editorOpen
            ? <WorksheetEditor form={form} setForm={setForm} editing={!!editingId} terms={terms} credentials={credentials} busy={busy} onSave={saveWorksheet} onCancel={() => { setEditorOpen(false); setForm(blankWorksheet()); if (!editingId) setSelectedWorksheet(null) }} onUpload={uploadFile} />
            : selectedWorksheet
              ? <WorksheetOverview key={selectedWorksheet.id} worksheet={selectedWorksheet} credentials={credentials} onBack={() => setSelectedWorksheet(null)} onEdit={editSelectedWorksheet} onOpenProduct={(productId)=>openWorksheetProduct(selectedWorksheet.id,productId)} onRefresh={()=>{void openWorksheet(selectedWorksheet.id);void loadDashboard(credentials)}} />
              : <WorksheetList items={visibleWorksheets} query={query} status={statusFilter} filters={taxonomyFilters} terms={terms} credentials={credentials} onQuery={setQuery} onStatus={setStatusFilter} onFilter={(type, ids) => setTaxonomyFilters((old) => ({ ...old, [type]: ids }))} onNew={openNewWorksheet} onOpen={openWorksheet} />}
        </>}
        {section === 'activities' && <ActivityView items={activities} form={activityForm} setForm={setActivityForm} terms={terms} busy={busy} onSave={saveActivity} onEdit={editActivity} />}
        {section === 'bundles' && <BundleView bundles={bundles} worksheets={worksheets} form={bundleForm} setForm={setBundleForm} busy={busy} onSave={saveBundle} onEdit={editBundle} />}
        {(section === 'finance'||section==='sales') && <FinanceView key="finance-view" initialTab={section==='sales'?'sales':'overview'} credentials={credentials} worksheets={worksheets} bundles={bundles} target={financeTarget} onTargetHandled={()=>setFinanceTarget(null)} onError={setError} notify={notify} />}
        {section === 'taxonomy' && <TaxonomyView terms={terms} credentials={credentials} reload={() => loadDashboard(credentials)} notify={notify} />}
          </>}
      </div>
    </main>
    {toast && <div className="toast"><Check size={17} />{toast}</div>}
  </div>
}

function Login({ username, password, error, onUsername, onPassword, onSubmit }: { username: string; password: string; error: string; onUsername: (s: string) => void; onPassword: (s: string) => void; onSubmit: (e: FormEvent) => void }) {
  return <div className="login-page"><div className="login-art"><div className="art-orbit orbit-one"/><div className="art-orbit orbit-two"/><div className="sun-disc"/><div className="art-leaf leaf-one">✳</div><div className="art-leaf leaf-two">✦</div><div className="art-note"><Flower2 size={20}/> A játék öröme<br/>minden apró lépésben</div></div><form className="login-card" onSubmit={onSubmit}><div className="brand-lockup login-brand"><div className="brand-mark"><Flower2 size={21}/></div><div><strong>apróLéptek</strong><span>MŰHELY · ADMIN</span></div></div><div className="login-heading"><span className="eyebrow">ÜDVÖZLÜNK A MŰHELYBEN</span><h1>Jó újra<br/>itt látni.</h1><p>Lépj be, hogy rendszerezhesd a készülő játékokat.</p></div><label className="field-label">Felhasználónév<input autoComplete="username" value={username} onChange={(e) => onUsername(e.target.value)} placeholder="admin"/></label><label className="field-label">Jelszó<input type="password" autoComplete="current-password" value={password} onChange={(e) => onPassword(e.target.value)} placeholder="Add meg a jelszavad"/></label>{error && <div className="login-error">{error}</div>}<button className="button primary full-width" type="submit">Belépés a műhelybe <ArrowLeft className="arrow-right" size={17}/></button><p className="login-foot"><ShieldCheck size={15}/> Biztonságos adminisztrátori hozzáférés</p></form></div>
}

function Overview({ dashboard, onNew, onNavigate, onOpen }: { dashboard: Dashboard | null; onNew: () => void; onNavigate: (s: Section) => void; onOpen: (id: string) => void }) {
  return <><div className="page-heading"><div><span className="eyebrow">{todayLabel}</span><h1>Szia, alkotó! <span className="heading-flower">✿</span></h1><p>Innen folytathatod az apró ötletek nagy kalandját.</p></div><button className="button primary" onClick={onNew}><Plus size={17}/> Új feladatlap</button></div><div className="welcome-banner"><div className="banner-copy"><span className="banner-kicker"><Sparkles size={14}/> A MŰHELYED</span><h2>Minden játékötlet<br/>egy helyen kezdődik.</h2><p>Rendezd, címkézd és készítsd elő az anyagaidat a következő nagy felfedezéshez.</p><button className="text-button" onClick={() => onNavigate('worksheets')}>Megnézem a feladatlapokat <ArrowLeft className="arrow-right" size={16}/></button></div><div className="banner-illustration"><div className="stack stack-back"/><div className="stack stack-middle"><span>1+1</span><i>✦</i></div><div className="stack stack-front"><Flower2 size={28}/><span>kis<br/>felfedező</span><b>✿</b></div><div className="float-star star-a">✦</div><div className="float-star star-b">✳</div></div></div><section className="section-block"><div className="section-title-row"><div><span className="eyebrow">GYORS ÁTTEKINTÉS</span><h2>A műhely állapota</h2></div><span className="updated-label"><span className="live-dot"/> Élő adatok</span></div><div className="stats-grid"><StatCard icon={<ClipboardList size={18}/>} tone="sage" label="Feladatlap" value={dashboard?.worksheets ?? '—'} note="a saját gyűjteményedben"/><StatCard icon={<Sparkles size={18}/>} tone="peach" label="Játékötlet" value={dashboard?.activities ?? '—'} note="önálló ötletként rögzítve"/><StatCard icon={<FileImage size={18}/>} tone="yellow" label="Piszkozat" value={dashboard?.drafts ?? '—'} note="még szerkesztés alatt"/></div></section><section className="section-block recent-block"><div className="section-title-row"><div><span className="eyebrow">LEGUTÓBBI MUNKÁK</span><h2>Folytasd, ahol abbahagytad</h2></div><button className="text-button muted" onClick={() => onNavigate('worksheets')}>Minden feladatlap <ArrowLeft className="arrow-right" size={15}/></button></div><div className="recent-list">{dashboard?.recentWorksheets?.length ? dashboard.recentWorksheets.map((item) => <button key={item.id} className="recent-item" onClick={() => onOpen(item.id)}><div className="recent-thumb"><Files size={19}/></div><div className="recent-info"><strong>{item.title}</strong><span>{item.total_pages ?? 0} nyomtatható oldal · A4</span></div><StatusPill status={item.status}/><MoreHorizontal size={19} className="more-icon"/></button>) : <div className="empty-state small"><div className="empty-icon"><FilePlus2 size={20}/></div><strong>Még nincs feladatlapod</strong><span>Hozd létre az elsőt, és itt folytathatod később.</span><button className="button secondary small-button" onClick={onNew}><Plus size={15}/> Első feladatlap létrehozása</button></div>}</div></section></>
}

function StatCard({ icon, tone, label, value, note }: { icon: ReactNode; tone: string; label: string; value: string | number; note: string }) { return <div className="stat-card"><div className={`stat-icon ${tone}`}>{icon}</div><div className="stat-label">{label}</div><strong className="stat-value">{value}</strong><span className="stat-note">{note}</span></div> }
function StatusPill({ status }: { status: string }) { const labels: Record<string,string> = { DRAFT:'Piszkozat', REVIEW:'Ellenőrzés', PUBLISHED:'Publikált', ARCHIVED:'Archivált' }; return <span className={`status-pill status-${status.toLowerCase()}`}><i/>{labels[status] ?? status}</span> }

function AuthenticatedImage({ id, credentials, alt, className }: { id:string; credentials:Credentials; alt:string; className:string }) {
  const [src,setSrc]=useState('')
  useEffect(()=>{let current=''; let cancelled=false; const bytes=new TextEncoder().encode(`${credentials.username}:${credentials.password}`);let binary='';bytes.forEach((byte)=>binary+=String.fromCharCode(byte));fetch(`${API}/media/${id}/content`,{headers:{Authorization:`Basic ${btoa(binary)}`}}).then((r)=>{if(!r.ok)throw new Error('preview');return r.blob()}).then((blob)=>{current=URL.createObjectURL(blob);if(!cancelled)setSrc(current)}).catch(()=>{});return()=>{cancelled=true;if(current)URL.revokeObjectURL(current)}},[id,credentials])
  return src ? <img src={src} alt={alt} className={className}/> : null
}

function AuthenticatedAsset({ id, credentials }: { id:string; credentials:Credentials }) {
  const [asset,setAsset]=useState<{url:string;type:string}|null>(null)
  useEffect(()=>{let current='';let cancelled=false;const bytes=new TextEncoder().encode(`${credentials.username}:${credentials.password}`);let binary='';bytes.forEach((byte)=>binary+=String.fromCharCode(byte));fetch(`${API}/media/${id}/content`,{headers:{Authorization:`Basic ${btoa(binary)}`}}).then((r)=>{if(!r.ok)throw new Error('preview');return r.blob()}).then((blob)=>{current=URL.createObjectURL(blob);if(!cancelled)setAsset({url:current,type:blob.type})}).catch(()=>{});return()=>{cancelled=true;if(current)URL.revokeObjectURL(current)}},[id,credentials])
  if(!asset)return <FileImage size={16}/>;
  return asset.type.startsWith('image/')?<img src={asset.url} alt=""/>:<span className="pdf-preview-link" title="PDF fájl"><FileImage size={15}/></span>;
}

function AssetPreviewModal({ id, label, credentials, onClose }: { id: string; label: string; credentials: Credentials; onClose: () => void }) {
  const [asset, setAsset] = useState<{ url: string; type: string } | null>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    let current = ''; let cancelled = false
    const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`)
    let binary = ''; bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
    fetch(`${API}/media/${id}/content`, { headers: { Authorization: `Basic ${btoa(binary)}` } })
      .then((response) => { if (!response.ok) throw new Error('preview'); return response.blob() })
      .then((blob) => { current = URL.createObjectURL(blob); if (!cancelled) setAsset({ url: current, type: blob.type }) })
      .catch(() => { if (!cancelled) setFailed(true) })
    return () => { cancelled = true; if (current) URL.revokeObjectURL(current) }
  }, [id, credentials])
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose])
  return <div className="asset-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="asset-modal" role="dialog" aria-modal="true" aria-label={`${label} előnézete`}><header><strong>{label}</strong><div>{asset && <a className="button secondary" href={asset.url} target="_blank" rel="noreferrer">Megnyitás külön lapon</a>}<button type="button" className="icon-button" onClick={onClose} aria-label="Bezárás"><X size={18}/></button></div></header><div className="asset-modal-content">{asset ? asset.type.startsWith('image/') ? <img src={asset.url} alt={label}/> : asset.type === 'application/pdf' ? <iframe src={asset.url} title={label}/> : <a href={asset.url} target="_blank" rel="noreferrer">Fájl megnyitása</a> : failed ? <span>Az előnézet nem tölthető be.</span> : <span>Előnézet betöltése…</span>}</div></section></div>
}

function WorksheetOverview({ worksheet, credentials, onBack, onEdit, onOpenProduct, onRefresh }: { worksheet: Worksheet; credentials: Credentials; onBack: () => void; onEdit: () => void; onOpenProduct:(productId?:string)=>void; onRefresh:()=>void }) {
  const files = new Map<string, { id: string; name: string; type: string }>()
  const addFile = (id?: string, name?: string, type?: string) => {
    if (!id || files.has(id)) return
    const lowerName = (name ?? '').toLocaleLowerCase('hu')
    const mediaType = type ?? (lowerName.endsWith('.pdf') ? 'application/pdf' : lowerName.match(/\.(png|jpe?g|webp)$/) ? 'image/unknown' : 'application/octet-stream')
    files.set(id, { id, name: name || worksheet.title, type: mediaType })
  }
  addFile(worksheet.preview_asset_id, worksheet.title, 'image/unknown')
  ;(worksheet.pages ?? []).forEach((page) => {
    addFile(page.preview_asset_id, page.preview_name, page.preview_media_type || 'image/unknown')
    addFile(page.print_asset_id, page.print_name, page.print_media_type)
  })
  const allFiles = Array.from(files.values())
  const images = allFiles.filter((file) => file.type.startsWith('image/'))
  const documents = allFiles.filter((file) => !file.type.startsWith('image/'))
  const [selectedIds, setSelectedIds] = useState<string[]>(images.map((file) => file.id))
  const [preview, setPreview] = useState<{ id: string; name: string } | null>(null)
  useEffect(()=>{window.scrollTo({top:0,behavior:'auto'})},[])
  const selectedImageIds = images.filter((file) => selectedIds.includes(file.id)).map((file) => file.id)
  const labels: Record<string, string> = { DRAFT: 'Piszkozat', REVIEW: 'Ellenőrzésre vár', PUBLISHED: 'Publikált', ARCHIVED: 'Archivált' }
  const usage: Record<string, string> = { DIGITAL: 'Digitális', PHYSICAL: 'Fizikai', BOTH: 'Digitális és fizikai' }
  return <>
    <div className="worksheet-detail-heading"><div><button className="back-link" onClick={onBack}><ArrowLeft size={15}/> Vissza a feladatlapokhoz</button><span className="eyebrow">FELADATLAP ÁTTEKINTÉSE</span><h1>{worksheet.title}</h1></div><button className="button primary" onClick={onEdit}><Settings2 size={15}/> Szerkesztés</button></div>
    <section className="worksheet-pricing-card"><div className="worksheet-detail-section-heading"><div><span className="eyebrow">ELADÁS ÉS ÖNKÖLTSÉG</span><h2>Eladható változatok</h2><p>Itt egy helyen látod és módosíthatod a változatok árát és anyagfelhasználását.</p></div><button type="button" className="button secondary" onClick={()=>onOpenProduct()}><Plus size={14}/>Változat hozzáadása</button></div>{worksheet.saleProducts?.length?<div className="worksheet-pricing-list">{worksheet.saleProducts.map((product)=>{const money=(value:number)=>new Intl.NumberFormat('hu-HU',{style:'currency',currency:'HUF',maximumFractionDigits:2}).format(value);return <div className="worksheet-pricing-item" key={product.id}><div className="worksheet-pricing-row"><span><strong>{product.variant_name}</strong><small>Anyag {money(Number(product.material_cost))} + nyomtatás {money(Number(product.printing_cost))} = gyártás {money(Number(product.manufacturing_cost))} / db · tervezett ár {money(Number(product.list_price))} · nyereség {money(Number(product.list_price)-Number(product.manufacturing_cost))} / db</small><small>{Number(product.units_sold)>0?`${Number(product.units_sold)} db eladva · ${money(Number(product.revenue))} bevétel · ${money(Number(product.revenue)-Number(product.sold_material_cost))} nyereség`:"Még nincs rögzített eladás"}</small></span></div><WorksheetProductInlineEditor credentials={credentials} product={product} onSaved={onRefresh}/></div>})}</div>:<div className="worksheet-pricing-empty"><span>Még nincs ehhez a feladatlaphoz eladható változat beállítva.</span><button type="button" className="text-button" onClick={()=>onOpenProduct()}>Ár és anyagköltség beállítása <ArrowLeft className="arrow-right" size={14}/></button></div>}</section>
    {images.length > 0 && <section className="worksheet-detail-gallery"><div className="worksheet-detail-section-heading"><div><span className="eyebrow">KÉPEK</span><h2>Nyomtatásra kész képek</h2><p>Jelöld ki, mely képeket szeretnéd nyomtatni.</p></div><span>{selectedImageIds.length} / {images.length} kijelölve</span></div><div className="detail-print-actions"><button className="text-button" onClick={() => setSelectedIds(images.map((file) => file.id))}>Összes kijelölése</button><button className="text-button muted" onClick={() => setSelectedIds([])}>Kijelölés törlése</button><button className="button secondary" onClick={() => printImageAssets(credentials, images.map((file) => file.id))}><Printer size={15}/> Összes kép nyomtatása</button><button className="button primary" disabled={!selectedImageIds.length} onClick={() => printImageAssets(credentials, selectedImageIds)}><Printer size={15}/> Kijelöltek nyomtatása</button></div><div className="worksheet-detail-images">{images.map((file) => <figure className={`worksheet-detail-image ${selectedIds.includes(file.id) ? 'selected' : ''}`} key={file.id}><button className="detail-image-open" type="button" onClick={() => setPreview(file)} aria-label={`${file.name} előnézete`}><AuthenticatedImage id={file.id} credentials={credentials} alt={file.name} className="detail-image"/><span>Előnézet</span></button><figcaption><label><input type="checkbox" checked={selectedIds.includes(file.id)} onChange={() => setSelectedIds((current) => current.includes(file.id) ? current.filter((id) => id !== file.id) : [...current, file.id])}/><span>{file.name}</span></label></figcaption></figure>)}</div></section>}
    <div className="worksheet-detail-data"><section className="worksheet-data-card"><span className="eyebrow">FELVITT ADATOK</span><h2>{worksheet.title}</h2>{worksheet.summary && <p className="worksheet-lead">{worksheet.summary}</p>}{worksheet.description && <div className="worksheet-data-block"><h3>Leírás</h3><p>{worksheet.description}</p></div>}{worksheet.instructions && <div className="worksheet-data-block"><h3>Útmutató</h3><p>{worksheet.instructions}</p></div>}{worksheet.safety_notes && <div className="worksheet-data-block"><h3>Biztonsági tudnivalók</h3><p>{worksheet.safety_notes}</p></div>}{worksheet.terms?.length ? <div className="worksheet-detail-tags">{worksheet.terms.map((term) => <span key={term.id}>{term.name}</span>)}</div> : null}</section><aside className="worksheet-facts-card"><span className="eyebrow">BEÁLLÍTÁSOK</span><dl><div><dt>Státusz</dt><dd>{labels[worksheet.status] ?? worksheet.status}</dd></div><div><dt>Felhasználás</dt><dd>{usage[worksheet.usage_type] ?? worksheet.usage_type}</dd></div><div><dt>Papírméret</dt><dd>{worksheet.paper_size === 'LETTER' ? 'Letter' : worksheet.paper_size}</dd></div><div><dt>Tájolás</dt><dd>{worksheet.orientation === 'LANDSCAPE' ? 'Fekvő' : 'Álló'}</dd></div><div><dt>Színmód</dt><dd>{worksheet.color_mode === 'GRAYSCALE' ? 'Fekete-fehér' : 'Színes'}</dd></div><div><dt>Oldalak száma</dt><dd>{worksheet.totalPages ?? worksheet.total_pages ?? worksheet.pages?.reduce((sum, page) => sum + (page.page_count || 1), 0) ?? 0}</dd></div></dl></aside></div>

    {documents.length > 0 && <section className="worksheet-documents"><div className="worksheet-detail-section-heading"><div><span className="eyebrow">CSATOLT DOKUMENTUMOK</span><h2>PDF-ek és egyéb fájlok</h2></div></div><div className="worksheet-document-list">{documents.map((file) => <button key={file.id} className="worksheet-document" onClick={() => setPreview(file)}><FileImage size={19}/><span>{file.name}</span><small>Előnézet megnyitása</small></button>)}</div></section>}
    {preview && <AssetPreviewModal id={preview.id} label={preview.name} credentials={credentials} onClose={() => setPreview(null)}/>}
  </>
}

function WorksheetProductInlineEditor({credentials,product,onSaved}:{credentials:Credentials;product:WorksheetSaleProduct;onSaved:()=>void}){
  const [listPrice,setListPrice]=useState(String(product.list_price))
  const [manualMaterialCost,setManualMaterialCost]=useState(String(product.material_cost))
  const [recipe,setRecipe]=useState((product.materials??[]).map((item)=>({materialId:item.material_id,quantity:Number(item.quantity),name:item.name,unitName:item.unit_name,unitCost:Number(item.unit_cost)})))
  const [saving,setSaving]=useState(false)
  const [error,setError]=useState('')
  const materialCost=recipe.length?recipe.reduce((sum,item)=>sum+item.quantity*item.unitCost,0):Number(manualMaterialCost)||0
  const money=(value:number)=>new Intl.NumberFormat('hu-HU',{style:'currency',currency:'HUF',maximumFractionDigits:2}).format(value)
  function reset(){setListPrice(String(product.list_price));setManualMaterialCost(String(product.material_cost));setRecipe((product.materials??[]).map((item)=>({materialId:item.material_id,quantity:Number(item.quantity),name:item.name,unitName:item.unit_name,unitCost:Number(item.unit_cost)})));setError('')}
  async function save(event:FormEvent){
    event.preventDefault();setSaving(true);setError('')
    try{
      await api(credentials,`/products/${product.id}`,{method:'PUT',body:JSON.stringify({contentType:'WORKSHEET',contentId:product.worksheet_id,variantName:product.variant_name,deliveryType:product.delivery_type,listPrice,materialCost:recipe.length?materialCost.toFixed(2):manualMaterialCost,printSides:product.print_sides,materials:recipe.map((item)=>({materialId:item.materialId,quantity:item.quantity}))})})
      onSaved()
    }catch(e){setError((e as Error).message)}finally{setSaving(false)}
  }
  return <form className="worksheet-inline-cost-editor" onSubmit={save}>
    <div className="worksheet-inline-cost-fields"><label className="field-label">Tervezett eladási ár (Ft)<input type="number" required min="1" step="0.01" value={listPrice} onChange={(event)=>setListPrice(event.target.value)}/></label>
      {recipe.length? <div className="worksheet-inline-materials"><strong>Anyagfelhasználás / db</strong>{recipe.map((item,index)=><label className="worksheet-inline-material" key={item.materialId}><span>{item.name}<small>{money(item.unitCost)} / {item.unitName}</small></span><span className="worksheet-inline-quantity"><input type="number" min="0.001" step="0.001" value={item.quantity} onChange={(event)=>setRecipe((current)=>current.map((line,lineIndex)=>lineIndex===index?{...line,quantity:Math.max(.001,Number(event.target.value)||.001)}:line))}/><small>{item.unitName}</small></span><b>{money(item.quantity*item.unitCost)}</b></label>)}</div>:product.delivery_type==='PHYSICAL'?<label className="field-label">Anyagköltség / db (Ft)<input type="number" min="0" step="0.01" value={manualMaterialCost} onChange={(event)=>setManualMaterialCost(event.target.value)}/></label>:<p className="finance-hint">Digitális terméknél az anyagköltség 0 Ft.</p>}
    </div>
    <div className="worksheet-inline-cost-total"><span>Anyagköltség / db <strong>{money(materialCost)}</strong></span><span>Nyomtatás / db <strong>{money(Number(product.printing_cost))}</strong></span><span>Teljes gyártási költség / db <strong>{money(materialCost+Number(product.printing_cost))}</strong></span><span>Tervezett nyereség / db <strong>{money(Number(listPrice||0)-materialCost-Number(product.printing_cost))}</strong></span></div>
    {error&&<p className="worksheet-inline-error">{error}</p>}
    <div className="worksheet-inline-actions"><button type="button" className="button secondary" onClick={reset}>Visszaállítás</button><button type="submit" className="button primary" disabled={saving}><Check size={14}/>{saving?'Mentés…':'Módosítások mentése'}</button></div>
  </form>
}

function WorksheetList({ items, query, status, filters, terms, credentials, onQuery, onStatus, onFilter, onNew, onOpen }: { items: Worksheet[]; query: string; status: string; filters: Record<Term['term_type'], string[]>; terms: Term[]; credentials: Credentials; onQuery: (s: string) => void; onStatus: (s: string) => void; onFilter: (type: Term['term_type'], ids: string[]) => void; onNew: () => void; onOpen: (id: string) => void }) {
  const groups: [Term['term_type'], string][] = [['AGE', 'Korosztály'], ['TASK_TYPE', 'Feladattípus'], ['TOPIC', 'Témakör'], ['SKILL', 'Készségterület']]
  const hasFilters = Boolean(query || status || Object.values(filters).some((ids) => ids.length > 0))
  return <><div className="page-heading"><div><span className="eyebrow">MŰHELY</span><h1>Feladatlapok</h1><p>Keress cím alapján, vagy szűrj a feladatlapok adatai szerint.</p></div><button className="button primary" onClick={onNew}><Plus size={17}/> Új feladatlap</button></div><div className="toolbar"><label className="search-box"><Search size={17}/><input aria-label="Feladatlapok keresése" placeholder="Keresés cím vagy rövid leírás alapján…" value={query} onChange={(event) => onQuery(event.target.value)}/>{query && <button type="button" className="clear-search" onClick={() => onQuery('')}><X size={15}/></button>}</label><label className="filter-select"><Filter size={16}/><select aria-label="Szűrés státusz szerint" value={status} onChange={(event) => onStatus(event.target.value)}><option value="">Minden állapot</option><option value="DRAFT">Piszkozat</option><option value="REVIEW">Ellenőrzésre vár</option><option value="PUBLISHED">Publikált</option><option value="ARCHIVED">Archivált</option></select><ChevronDown size={14}/></label>{groups.map(([type, label]) => <TaxonomyFilterMenu key={type} type={type} label={label} terms={terms} selectedIds={filters[type]} onChange={(ids) => onFilter(type, ids)}/>)}<span className="result-count">{items.length} feladatlap</span></div>{items.length ? <div className="worksheet-cover-grid">{items.map((item) => <article className="worksheet-cover-card" key={item.id}><button type="button" className="worksheet-cover-open" onClick={() => onOpen(item.id)}><span className="worksheet-cover-image">{item.preview_asset_id ? <AuthenticatedImage id={item.preview_asset_id} credentials={credentials} alt="" className="worksheet-cover-photo"/> : <Files size={34}/>}</span><strong>{item.title}</strong></button></article>)}</div> : <div className="empty-state"><div className="empty-icon"><Files size={22}/></div><strong>{hasFilters ? 'Nincs ilyen feladatlap' : 'Még nincs feladatlap'}</strong><span>{hasFilters ? 'Törölj egy vagy több szűrőt, vagy próbálj másik keresést.' : 'Hozd létre az első feladatlapot.'}</span>{hasFilters ? <button className="button secondary" onClick={() => { onQuery(''); onStatus(''); (Object.keys(filters) as Term['term_type'][]).forEach((type) => onFilter(type, [])) }}>Szűrők törlése</button> : <button className="button secondary" onClick={onNew}><Plus size={16}/> Feladatlap létrehozása</button>}</div>}</>
}

function TaxonomyFilterMenu({ type, label, terms, selectedIds, onChange }: { type: Term['term_type']; label: string; terms: Term[]; selectedIds: string[]; onChange: (ids: string[]) => void }) {
  const options = terms.filter((term) => term.term_type === type && term.active !== false)
  return <details className="filter-menu"><summary>{label}{selectedIds.length > 0 && <span className="filter-count">{selectedIds.length}</span>}<ChevronDown size={13}/></summary><div className="filter-menu-options">{options.length ? options.map((term) => <label key={term.id}><input type="checkbox" checked={selectedIds.includes(term.id)} onChange={(event) => onChange(event.target.checked ? [...selectedIds, term.id] : selectedIds.filter((id) => id !== term.id))}/><span>{term.name}</span></label>) : <span className="muted-small">Még nincs felvéve érték</span>}</div></details>
}

function printImageAssets(credentials: Credentials, ids: string[]) {
  const printWindow = window.open('', '_blank')
  if (!printWindow) return
  printWindow.document.write('<!doctype html><title>Képek előkészítése a nyomtatáshoz</title><p style="font:16px sans-serif;padding:24px">Képek előkészítése…</p>')
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`)
  let binary = ''; bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  const authorization = `Basic ${btoa(binary)}`
  Promise.all(ids.map((id) => fetch(`${API}/media/${id}/content`, { headers: { Authorization: authorization } }).then((response) => {
    if (!response.ok) throw new Error('A kép nem tölthető be.')
    return response.blob()
  }).then((blob) => URL.createObjectURL(blob)))).then((urls) => {
    printWindow.document.open()
    printWindow.document.write(`<!doctype html><html><head><title>Képek nyomtatása</title><style>@page{margin:8mm}body{margin:0}.print-page{height:calc(100vh - 16mm);display:grid;place-items:center;break-after:page;page-break-after:always}.print-page:last-child{break-after:auto;page-break-after:auto}img{max-width:100%;max-height:100%;object-fit:contain}@media screen{body{background:#eee}.print-page{height:calc(100vh - 32px);padding:16px;box-sizing:border-box}}</style></head><body>${urls.map((url) => `<div class="print-page"><img src="${url}"></div>`).join('')}<script>window.onload=()=>setTimeout(()=>window.print(),350)<\/script></body></html>`)
    printWindow.document.close()
  }).catch(() => {
    printWindow.document.body.innerHTML = '<p style="font:16px sans-serif;padding:24px">Nem sikerült betölteni a kijelölt képeket. Zárd be ezt az ablakot, és próbáld újra.</p>'
  })
}

function WorksheetEditor({ form, setForm, editing, terms, credentials, busy, onSave, onCancel, onUpload }: { form: Worksheet; setForm: Dispatch<SetStateAction<Worksheet>>; editing: boolean; terms: Term[]; credentials:Credentials; busy: boolean; onSave: (e: FormEvent) => void; onCancel: () => void; onUpload: (files: File[], pageIndex: number, target: 'preview_asset_id'|'print_asset_id') => void }) {
  const pages = form.pages ?? []
  const [excludedPrintIds, setExcludedPrintIds] = useState<string[]>([])
  const taskTypeDefaults = terms.filter((term) => term.term_type === 'TASK_TYPE' && form.terms?.some((selected) => selected.id === term.id) && (term.default_description?.trim() || term.default_instructions?.trim()))
  function updatePage(index: number, key: keyof Page, value: string | number) { setForm((old) => ({ ...old, pages: old.pages?.map((p,i) => i === index ? { ...p, [key]: value } as Page : p) })) }
  function addCutout() { setForm((old) => ({ ...old, pages: [...(old.pages ?? []), blankPage('CUTOUT', old.pages?.length ?? 1)] })) }
  function movePage(index: number, direction: -1 | 1) { setForm((old) => { const next = [...(old.pages ?? [])]; const target = index + direction; if (target < 1 || target >= next.length) return old; [next[index],next[target]] = [next[target],next[index]]; return { ...old, pages: next.map((p,i) => ({...p,sort_order:i})) } }) }
  function removePage(index: number) { setForm((old) => ({ ...old, pages: old.pages?.filter((_,i) => i !== index).map((p,i) => ({...p,sort_order:i})) })) }
  const totalPages = pages.reduce((total,page) => total + (Number(page.page_count) || 1),0)
  const printableImages = pages.filter((page) => page.print_asset_id && page.print_media_type?.startsWith('image/'))
  const selectedImages = printableImages.filter((page) => !excludedPrintIds.includes(page.print_asset_id!))
  function selectAllImages() { setExcludedPrintIds([]) }
  function togglePrintImage(id: string) { setExcludedPrintIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]) }
  function applyTaskTypeDefaults(term: Term) {
    if ((form.description?.trim() || form.instructions?.trim()) && !window.confirm('A meglévő általános leírás vagy útmutató lecserélődik. Folytatod?')) return
    setForm((current) => ({ ...current, description: term.default_description ?? current.description, instructions: term.default_instructions ?? current.instructions }))
  }
  return <form className="editor-form" onSubmit={onSave}><div className="page-heading editor-heading"><div><button type="button" className="back-link" onClick={onCancel}><ArrowLeft size={15}/> Vissza a listához</button><span className="eyebrow">{editing ? 'TARTALOM SZERKESZTÉSE' : 'ÚJ TARTALOM'}</span><h1>{editing ? 'Feladatlap szerkesztése' : 'Új feladatlap'}</h1><p>Rögzítsd az alaplapot, majd add hozzá a kivágható oldalakat.</p></div><div className="heading-actions"><button type="button" className="button secondary" onClick={onCancel}>Mégse</button><button className="button primary" disabled={busy}><Check size={16}/>{busy ? 'Mentés…' : 'Mentés piszkozatként'}</button></div></div><div className="editor-layout"><div className="editor-main"><section className="form-card"><div className="form-card-heading"><div className="step-number">01</div><div><h2>Alapadatok</h2><p>Adj nevet és rövid útmutatást a feladatlaphoz.</p></div></div><div className="form-grid"><label className="field-label span-2">Feladatlap címe *<input required value={form.title} onChange={(e) => setForm((old) => ({ ...old,title:e.target.value }))} placeholder="Például: Melyik állat hova bújt?"/></label><label className="field-label span-2">Rövid leírás<textarea rows={2} value={form.summary ?? ''} onChange={(e) => setForm((old) => ({ ...old,summary:e.target.value }))} placeholder="Röviden, mit talál a feladatlapon?"/></label><label className="field-label span-2">Általános leírás<textarea rows={3} value={form.description ?? ''} onChange={(e) => setForm((old) => ({ ...old,description:e.target.value }))} placeholder="Mi a feladat lényege?"/></label><label className="field-label span-2">Használati útmutató<textarea rows={3} value={form.instructions ?? ''} onChange={(e) => setForm((old) => ({ ...old,instructions:e.target.value }))} placeholder="Hogyan használja a szülő vagy pedagógus?"/></label><label className="field-label">Papírméret<select value={form.paper_size} onChange={(e) => setForm((old) => ({ ...old,paper_size:e.target.value }))}><option>A4</option><option>A3</option><option value="LETTER">Letter</option></select></label><label className="field-label">Tájolás<select value={form.orientation} onChange={(e) => setForm((old) => ({ ...old,orientation:e.target.value }))}><option value="PORTRAIT">Álló</option><option value="LANDSCAPE">Fekvő</option></select></label><label className="field-label">Színmód<select value={form.color_mode} onChange={(e) => setForm((old) => ({ ...old,color_mode:e.target.value }))}><option value="COLOR">Színes</option><option value="GRAYSCALE">Fekete-fehér</option></select></label><label className="field-label">Felhasználás<select value={form.usage_type} onChange={(e) => setForm((old) => ({ ...old,usage_type:e.target.value }))}><option value="DIGITAL">Digitális</option><option value="PHYSICAL">Fizikai</option><option value="BOTH">Mindkettő</option></select></label><label className="field-label span-2">Biztonsági megjegyzés<textarea rows={2} value={form.safety_notes ?? ''} onChange={(e) => setForm((old) => ({ ...old,safety_notes:e.target.value }))} placeholder="Például: a kivágást felnőtt végezze el."/></label></div></section>
    <section className="form-card pages-card"><div className="form-card-heading"><div className="step-number">02</div><div><h2>Nyomtatható oldalak</h2><p>Az alap feladatlaphoz további kivágható lapokat rendelhetsz.</p></div><span className="page-total">{pages.length} lap · {totalPages} oldal</span></div>{pages.map((page,index) => <div className={`page-editor ${page.page_type === 'MAIN' ? 'main-page-editor' : ''}`} key={`${page.page_type}-${index}`}><div className="page-editor-title"><div className="page-type-icon">{page.page_type === 'MAIN' ? <ClipboardList size={17}/> : <Files size={17}/>}</div><div><strong>{page.page_type === 'MAIN' ? 'Alap feladatlap' : `Kivágható lap ${index}`}</strong><span>{page.page_type === 'MAIN' ? 'A feladat fő oldala' : 'A feladatlaphoz tartozó kiegészítő'}</span></div><div className="page-order-actions"><button type="button" className="icon-button" disabled={index <= 1} title="Mozgatás fel" onClick={() => movePage(index,-1)}><ArrowUp size={15}/></button><button type="button" className="icon-button" disabled={index === 0 || index === pages.length-1} title="Mozgatás le" onClick={() => movePage(index,1)}><ArrowDown size={15}/></button>{page.page_type === 'CUTOUT' && <button type="button" className="icon-button remove-button" title="Lap törlése" onClick={() => removePage(index)}><X size={16}/></button>}</div></div><div className="page-form-grid"><label className="field-label">Lap megnevezése<input value={page.title ?? ''} disabled={page.page_type === 'MAIN'} onChange={(e) => updatePage(index,'title',e.target.value)} placeholder="Például: Kivágható állatfigurák"/></label><label className="field-label narrow-field">Oldalszám<input type="number" min="1" value={page.page_count} onChange={(e) => updatePage(index,'page_count',Number(e.target.value))}/></label><label className="field-label narrow-field">Tájolás<select value={page.orientation} onChange={(e) => updatePage(index,'orientation',e.target.value)}><option value="PORTRAIT">Álló</option><option value="LANDSCAPE">Fekvő</option></select></label><label className="field-label">Megjegyzés<input value={page.notes ?? ''} onChange={(e) => updatePage(index,'notes',e.target.value)} placeholder="Opcionális útmutató"/></label></div><div className="upload-row"><UploadBox label="Előnézeti kép" accept="image/png,image/jpeg,image/webp" assetId={page.preview_asset_id} credentials={credentials} assetName={page.preview_name} onUpload={(files) => onUpload(files,index,'preview_asset_id')}/><UploadBox label="Nyomtatási fájl" accept="application/pdf,image/png,image/jpeg,image/webp" assetId={page.print_asset_id} assetName={page.print_name} credentials={credentials} multiple onUpload={(files) => onUpload(files,index,'print_asset_id')}/></div></div>)}{printableImages.length > 0 && <div className="bulk-print-panel"><div className="bulk-print-heading"><div><strong>Képek nyomtatása</strong><span>{selectedImages.length} / {printableImages.length} kép kijelölve</span></div><div><button type="button" className="text-button" onClick={selectAllImages}>Összes kijelölése</button><button type="button" className="text-button muted" onClick={() => setExcludedPrintIds(printableImages.map((page) => page.print_asset_id!))}>Kijelölés törlése</button><button type="button" className="button secondary" disabled={!selectedImages.length} onClick={() => printImageAssets(credentials, selectedImages.map((page) => page.print_asset_id!))}><Printer size={15}/> Nyomtatás</button></div></div><div className="bulk-print-files">{printableImages.map((page) => <label key={page.print_asset_id}><input type="checkbox" checked={!excludedPrintIds.includes(page.print_asset_id!)} onChange={() => togglePrintImage(page.print_asset_id!)}/><span>{page.print_name || page.title || 'Kép'}</span></label>)}</div></div>}<button type="button" className="add-cutout" onClick={addCutout}><Plus size={16}/> Kivágható lap hozzáadása</button></section></div><aside className="editor-aside"><section className="form-card side-form-card"><div className="form-card-heading compact"><div className="step-number">03</div><div><h2>Kategorizálás</h2><p>Segíts, hogy később könnyű legyen megtalálni.</p></div></div>{([['AGE','KOROSZTÁLY'],['TASK_TYPE','FELADATTÍPUS'],['TOPIC','TÉMAKÖR'],['SKILL','KÉSZSÉGTERÜLET']] as [Term['term_type'],string][]).map(([type,label]) => <fieldset className="term-fieldset" key={type}><legend>{label}</legend><div className="term-options">{terms.filter((term) => term.term_type === type && (term.active !== false || form.terms?.some((selected) => selected.id === term.id))).map((term) => { const selected = form.terms?.some((t) => t.id === term.id); return <button type="button" key={term.id} disabled={term.active === false && selected} className={`term-chip ${selected?'selected':''}`} onClick={() => setForm((old) => ({ ...old,terms:selected ? old.terms?.filter((t) => t.id !== term.id) : [...(old.terms ?? []),term] }))}>{selected && <Check size={13}/>} {term.name}{term.active === false ? ' · inaktív' : ''}</button> })}{!terms.some((term) => term.term_type === type) && <span className="muted-small">Még nincs felvéve elem</span>}</div></fieldset>)}{taskTypeDefaults.length > 0 && <div className="task-type-default-actions"><strong>Feladattípus-alapleírás</strong><span>Válassz egy típust, hogy betöltsd az általános leírását és az útmutatóját.</span>{taskTypeDefaults.map((term) => <button type="button" className="button secondary" key={term.id} onClick={() => applyTaskTypeDefaults(term)}><FilePlus2 size={14}/> Alapleírás betöltése: {term.name}</button>)}</div>}<label className="field-label status-label">Tartalom státusza<select value={form.status} onChange={(e) => setForm((old) => ({ ...old,status:e.target.value }))}><option value="DRAFT">Piszkozat</option><option value="REVIEW">Ellenőrzésre vár</option><option value="PUBLISHED">Publikált</option></select></label><div className="publish-note"><ShieldCheck size={15}/><span>A publikált tartalom később megjelenhet a szülői katalógusban.</span></div></section><section className="print-summary"><div className="print-summary-icon"><Files size={17}/></div><div><span>NYOMTATÁSI ÖSSZESÍTŐ</span><strong>{pages.length} elem · {totalPages} oldal</strong><small>{form.paper_size} · {form.orientation === 'LANDSCAPE' ? 'fekvő' : 'álló'} · {form.color_mode === 'GRAYSCALE' ? 'fekete-fehér' : 'színes'}</small></div></section></aside></div></form>
}

function UploadBox({ label, accept, assetId, assetName, credentials, multiple = false, onUpload }: { label: string; accept: string; assetId?: string; assetName?: string; credentials:Credentials; multiple?: boolean; onUpload: (files: File[]) => void }) {
  const [previewOpen, setPreviewOpen] = useState(false)
  return <div className="upload-control"><label className={`upload-box ${assetId ? 'has-file' : ''}`}><input type="file" accept={accept} multiple={multiple} onChange={(e) => { const files=Array.from(e.currentTarget.files ?? []); if(files.length) onUpload(files); e.currentTarget.value='' }}/>{assetId ? <><div className="asset-preview"><AuthenticatedAsset id={assetId} credentials={credentials}/></div><div><strong>{assetName || label}</strong><span>Feltöltve · kattints a cseréhez</span></div></> : <><div className="upload-icon"><Upload size={16}/></div><div><strong>{label}</strong><span>{multiple ? 'Több kép vagy PDF kijelölhető · 20 MB/fájl, 24 MB összesen' : 'PDF vagy kép · max. 20 MB'}</span></div></>}</label>{assetId && <button type="button" className="button secondary preview-file-button" onClick={() => setPreviewOpen(true)}>Előnézet</button>}{previewOpen && assetId && <AssetPreviewModal id={assetId} label={assetName || label} credentials={credentials} onClose={() => setPreviewOpen(false)}/>}</div>
}

function ActivityView({ items, form, setForm, terms, busy, onSave, onEdit }: { items: ActivityItem[]; form: ActivityForm; setForm: Dispatch<SetStateAction<ActivityForm>>; terms:Term[]; busy: boolean; onSave: (e: FormEvent) => void; onEdit:(id:string)=>void }) {
  return <><div className="page-heading"><div><span className="eyebrow">ÖNÁLLÓ TARTALOM</span><h1>Játékötletek</h1><p>Leírásként is megálló játékok, amelyekhez nem kell feladatlap.</p></div></div><div className="activity-layout"><form className="form-card activity-form" onSubmit={onSave}><div className="form-card-heading"><div className="step-number"><Sparkles size={17}/></div><div><h2>{form.id ? 'Játékötlet szerkesztése' : 'Új játékötlet'}</h2><p>Rögzítsd a játék alapadatait.</p></div>{form.id&&<button type="button" className="icon-button" title="Új játékötlet" onClick={()=>setForm({id:'',title:'',summary:'',durationMinutes:'',instructions:'',materials:'',preparation:'',easyVariant:'',challengeVariant:'',safetyNotes:'',locationType:'ANY',status:'DRAFT',termIds:[]})}><Plus size={16}/></button>}</div><label className="field-label">Játék neve *<input required value={form.title} onChange={(e)=>setForm((old)=>({...old,title:e.target.value}))} placeholder="Például: Színes kupakvadászat"/></label><label className="field-label">Rövid leírás<textarea rows={3} value={form.summary} onChange={(e)=>setForm((old)=>({...old,summary:e.target.value}))} placeholder="Miről szól a játék?"/></label><label className="field-label">Játék menete<textarea rows={5} value={form.instructions} onChange={(e)=>setForm((old)=>({...old,instructions:e.target.value}))} placeholder="Írd le lépésről lépésre…"/></label><label className="field-label">Szükséges eszközök<textarea rows={2} value={form.materials} onChange={(e)=>setForm((old)=>({...old,materials:e.target.value}))} placeholder="Mire lesz szükségetek?"/></label><label className="field-label">Előkészület<textarea rows={2} value={form.preparation} onChange={(e)=>setForm((old)=>({...old,preparation:e.target.value}))} placeholder="Mit készíts elő a játék előtt?"/></label><div className="two-fields"><label className="field-label">Könnyített változat<textarea rows={3} value={form.easyVariant} onChange={(e)=>setForm((old)=>({...old,easyVariant:e.target.value}))}/></label><label className="field-label">Nehezített változat<textarea rows={3} value={form.challengeVariant} onChange={(e)=>setForm((old)=>({...old,challengeVariant:e.target.value}))}/></label></div><label className="field-label">Biztonsági tudnivalók<textarea rows={2} value={form.safetyNotes} onChange={(e)=>setForm((old)=>({...old,safetyNotes:e.target.value}))} placeholder="Felnőtt felügyelete, apró elemek stb."/></label><label className="field-label">Helyszín<select value={form.locationType} onChange={(e)=>setForm((old)=>({...old,locationType:e.target.value}))}><option value="ANY">Benti vagy kinti</option><option value="INDOOR">Benti</option><option value="OUTDOOR">Kinti</option></select></label><label className="field-label">Korosztályok, témakörök és készségterületek · több is választható<select multiple size={5} value={form.termIds} onChange={(e)=>setForm((old)=>({...old,termIds:Array.from(e.target.selectedOptions).map((option)=>option.value)}))}>{terms.filter((term)=>term.term_type!=='TASK_TYPE'&&(term.active!==false||form.termIds.includes(term.id))).map((term)=><option key={term.id} value={term.id} disabled={term.active===false}>{term.term_type==='AGE'?'Korosztály':term.term_type==='TOPIC'?'Témakör':'Készségterület'} · {term.name}{term.active===false?' · inaktív':''}</option>)}</select></label><div className="two-fields"><label className="field-label">Időtartam (perc)<input type="number" min="1" value={form.durationMinutes} onChange={(e)=>setForm((old)=>({...old,durationMinutes:e.target.value}))} placeholder="10"/></label><label className="field-label">Státusz<select value={form.status} onChange={(e)=>setForm((old)=>({...old,status:e.target.value}))}><option value="DRAFT">Piszkozat</option><option value="REVIEW">Ellenőrzés</option><option value="PUBLISHED">Publikált</option></select></label></div><button className="button primary full-width" disabled={busy}><Check size={16}/>{busy?'Mentés…':'Játékötlet mentése'}</button></form><div className="activity-side"><div className="section-title-row"><div><span className="eyebrow">GYŰJTEMÉNY</span><h2>Rögzített ötletek</h2></div><span className="count-badge">{items.length}</span></div>{items.length ? items.map((item)=><button className="activity-item activity-item-button" key={item.id} onClick={()=>onEdit(item.id)}><div className="activity-item-icon"><Sparkles size={17}/></div><div><strong>{item.title}</strong><span>{item.duration_minutes ? `${item.duration_minutes} perc · ` : ''}{item.summary || 'Nincs rövid leírás'}</span></div><StatusPill status={item.status}/></button>) : <div className="empty-state small"><div className="empty-icon"><BookOpen size={20}/></div><strong>Még nincs játékötlet</strong><span>Az első leírás lehet teljesen önálló tartalom.</span></div>}</div></div></>
}

function TaxonomyView({ terms, credentials, reload, notify }: { terms: Term[]; credentials: Credentials; reload: () => Promise<void>; notify: (s: string) => void }) {
  const [name, setName] = useState('')
  const [type, setType] = useState<Term['term_type']>('TOPIC')
  const [busy, setBusy] = useState(false)
  const [savingTemplate, setSavingTemplate] = useState<string | null>(null)
  const [savingTerm, setSavingTerm] = useState<string | null>(null)
  const [templates, setTemplates] = useState<Record<string, { description: string; instructions: string }>>({})
  const [termDrafts, setTermDrafts] = useState<Record<string, { name: string; slug: string; sortOrder: string; active: boolean }>>({})

  useEffect(() => {
    setTemplates(Object.fromEntries(terms.filter((term) => term.term_type === 'TASK_TYPE').map((term) => [term.id, {
      description: term.default_description ?? '', instructions: term.default_instructions ?? '',
    }])))
    setTermDrafts(Object.fromEntries(terms.map((term) => [term.id, {
      name: term.name, slug: term.slug, sortOrder: String(term.sort_order ?? 0), active: term.active !== false,
    }])))
  }, [terms])

  async function create(event: FormEvent) {
    event.preventDefault(); setBusy(true)
    try { await api(credentials, '/taxonomy', { method: 'POST', body: JSON.stringify({ name, termType: type }) }); setName(''); await reload(); notify('Kategória hozzáadva') }
    catch (e) { notify((e as Error).message) } finally { setBusy(false) }
  }

  async function saveTemplate(term: Term) {
    const value = templates[term.id] ?? { description: '', instructions: '' }
    setSavingTemplate(term.id)
    try {
      await api(credentials, `/taxonomy/${term.id}/defaults`, { method: 'PUT', body: JSON.stringify({ defaultDescription: value.description, defaultInstructions: value.instructions }) })
      await reload(); notify(`${term.name} alapleírása elmentve`)
    } catch (e) { notify((e as Error).message) } finally { setSavingTemplate(null) }
  }

  async function saveTerm(term: Term) {
    const draft = termDrafts[term.id]
    if (!draft) return
    setSavingTerm(term.id)
    try {
      await api(credentials, `/taxonomy/${term.id}`, { method: 'PUT', body: JSON.stringify({ name: draft.name, slug: draft.slug, sortOrder: Number(draft.sortOrder), active: draft.active }) })
      await reload(); notify(`${term.name} kategória frissítve`)
    } catch (e) { notify((e as Error).message) } finally { setSavingTerm(null) }
  }

  const groups: [Term['term_type'], string][] = [['AGE', 'Korosztályok'], ['TASK_TYPE', 'Feladattípusok'], ['TOPIC', 'Témakörök'], ['SKILL', 'Készségterületek']]
  return <><div className="page-heading"><div><span className="eyebrow">KÖZÖS KATALÓGUS</span><h1>Kategóriák</h1><p>A kategóriák neve, URL-azonosítója és megjelenési sorrendje is szerkeszthető. A feladattípusokhoz közös leírásokat és útmutatókat adhatsz meg.</p></div></div><div className="taxonomy-layout"><form className="form-card taxonomy-form" onSubmit={create}><div className="form-card-heading"><div className="step-number"><Plus size={17}/></div><div><h2>Új kategória</h2><p>Adj hozzá egy új választható címkét.</p></div></div><label className="field-label">Kategória típusa<select value={type} onChange={(e) => setType(e.target.value as Term['term_type'])}><option value="AGE">Korosztály</option><option value="TASK_TYPE">Feladattípus</option><option value="TOPIC">Témakör</option><option value="SKILL">Készségterület</option></select></label><label className="field-label">Megnevezés<input required value={name} onChange={(e) => setName(e.target.value)} placeholder={type === 'AGE' ? 'Például: 2–3 év' : type === 'TASK_TYPE' ? 'Például: Árnyékpárosító' : type === 'TOPIC' ? 'Például: Állatok' : 'Például: Finommotorika'}/></label><button className="button primary" disabled={busy}><Plus size={16}/> Hozzáadás</button></form><div className="taxonomy-groups">{groups.map(([group, title]) => <section className="form-card taxonomy-group" key={group}><div className="section-title-row"><div><span className="eyebrow">{group}</span><h2>{title}</h2></div><span className="count-badge">{terms.filter((term) => term.term_type === group).length}</span></div><div className="task-type-default-list">{terms.filter((term) => term.term_type === group).map((term) => { const draft = termDrafts[term.id] ?? { name: term.name, slug: term.slug, sortOrder: String(term.sort_order ?? 0), active: term.active !== false }; const value = templates[term.id] ?? { description: '', instructions: '' }; return <details className="task-type-default-card" key={term.id}><summary><strong>{term.name}</strong><span>{term.slug} · #{term.sort_order ?? 0}</span></summary><div className="task-type-default-fields"><label className="field-label">Kategória neve<input maxLength={120} required value={draft.name} onChange={(event) => setTermDrafts((current) => ({ ...current, [term.id]: { ...draft, name: event.target.value } }))}/></label><div className="two-fields"><label className="field-label">Slug<input maxLength={140} required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" value={draft.slug} onChange={(event) => setTermDrafts((current) => ({ ...current, [term.id]: { ...draft, slug: event.target.value } }))}/></label><label className="field-label">Megjelenítési sorrend<input type="number" min={0} max={10000} required value={draft.sortOrder} onChange={(event) => setTermDrafts((current) => ({ ...current, [term.id]: { ...draft, sortOrder: event.target.value } }))}/></label></div><label className="field-label"><span><input type="checkbox" checked={draft.active} style={{width: 'auto', height: 'auto'}} onChange={(event) => setTermDrafts((current) => ({ ...current, [term.id]: { ...draft, active: event.target.checked } }))}/> Aktív kategória</span></label><button type="button" className="button secondary" disabled={savingTerm === term.id} onClick={() => saveTerm(term)}><Check size={14}/>{savingTerm === term.id ? 'Mentés…' : 'Kategória mentése'}</button>{group === 'TASK_TYPE' && <><label className="field-label">Általános leírás<textarea rows={3} value={value.description} onChange={(event) => setTemplates((current) => ({ ...current, [term.id]: { ...value, description: event.target.value } }))} placeholder="Mi jellemző erre a feladattípusra?"/></label><label className="field-label">Használati útmutató<textarea rows={3} value={value.instructions} onChange={(event) => setTemplates((current) => ({ ...current, [term.id]: { ...value, instructions: event.target.value } }))} placeholder="Hogyan érdemes használni vagy előkészíteni?"/></label><button type="button" className="button secondary" disabled={savingTemplate === term.id} onClick={() => saveTemplate(term)}><Check size={14}/>{savingTemplate === term.id ? 'Mentés…' : 'Alapértelmezés mentése'}</button></>}</div></details> })}{!terms.some((term) => term.term_type === group) && <span className="muted-small">Még nincs kategória. Hozz létre egyet a bal oldalon.</span>}</div></section>)}</div></div></>
}

function BundleView({ bundles, worksheets, form, setForm, busy, onSave, onEdit }: { bundles:BundleItem[]; worksheets:Worksheet[]; form:BundleForm; setForm:Dispatch<SetStateAction<BundleForm>>; busy:boolean; onSave:(e:FormEvent)=>void; onEdit:(id:string)=>void }) {
  return <><div className="page-heading"><div><span className="eyebrow">KÉSŐBBI ÉRTÉKESÍTÉSHEZ ELŐKÉSZÍTVE</span><h1>Csomagtervek</h1><p>Állíts össze több feladatlapból digitális vagy fizikai ajánlatot.</p></div></div><div className="bundle-layout"><form className="form-card bundle-form" onSubmit={onSave}><div className="form-card-heading"><div className="step-number"><Package size={17}/></div><div><h2>{form.id?'Csomag szerkesztése':'Új csomagterv'}</h2><p>A kivágható oldalak a saját feladatlapjukkal együtt kerülnek a csomagba.</p></div>{form.id&&<button type="button" className="icon-button" title="Új csomag" onClick={()=>setForm({id:'',title:'',slug:'',description:'',bundleType:'DIGITAL',status:'DRAFT',worksheetIds:[]})}><Plus size={16}/></button>}</div><label className="field-label">Csomag neve *<input required value={form.title} onChange={(e)=>setForm((old)=>({...old,title:e.target.value}))} placeholder="Például: Tavaszi állatos kalandok"/></label><label className="field-label">Rövid leírás<textarea rows={3} value={form.description} onChange={(e)=>setForm((old)=>({...old,description:e.target.value}))} placeholder="Kiknek és milyen témában készült?"/></label><div className="two-fields"><label className="field-label">Ajánlat formája<select value={form.bundleType} onChange={(e)=>setForm((old)=>({...old,bundleType:e.target.value}))}><option value="DIGITAL">Digitális letöltés</option><option value="PHYSICAL">Laminált fizikai csomag</option><option value="BOTH">Digitális és fizikai</option></select></label><label className="field-label">Státusz<select value={form.status} onChange={(e)=>setForm((old)=>({...old,status:e.target.value}))}><option value="DRAFT">Piszkozat</option><option value="REVIEW">Ellenőrzés</option><option value="PUBLISHED">Publikált</option></select></label></div><fieldset className="bundle-worksheets"><legend>FELADATLAPOK A CSOMAGBAN · {form.worksheetIds.length}</legend>{worksheets.filter((w)=>w.status!=='ARCHIVED').length?worksheets.filter((w)=>w.status!=='ARCHIVED').map((worksheet)=><label className="bundle-choice" key={worksheet.id}><input type="checkbox" checked={form.worksheetIds.includes(worksheet.id)} onChange={(e)=>setForm((old)=>({...old,worksheetIds:e.target.checked?[...old.worksheetIds,worksheet.id]:old.worksheetIds.filter((id)=>id!==worksheet.id)}))}/><span className="bundle-check"><Check size={12}/></span><span className="bundle-choice-name"><strong>{worksheet.title}</strong><small>{worksheet.total_pages??worksheet.totalPages??1} oldal · {worksheet.paper_size??'A4'}</small></span><StatusPill status={worksheet.status}/></label>):<span className="muted-small">Előbb hozz létre feladatlapot.</span>}</fieldset><button className="button primary full-width" disabled={busy}><Check size={16}/>{busy?'Mentés…':'Csomagterv mentése'}</button></form><div className="bundle-list"><div className="section-title-row"><div><span className="eyebrow">ÖSSZEÁLLÍTÁSOK</span><h2>Mentett csomagtervek</h2></div><span className="count-badge">{bundles.length}</span></div>{bundles.length?bundles.map((bundle)=><button className="bundle-card" key={bundle.id} onClick={()=>onEdit(bundle.id)}><div className="bundle-card-icon"><Package size={19}/></div><span className="bundle-card-main"><strong>{bundle.title}</strong><small>{bundle.worksheet_count??0} feladatlap · {bundle.bundle_type==='PHYSICAL'?'Fizikai':bundle.bundle_type==='BOTH'?'Digitális + fizikai':'Digitális'}</small></span><StatusPill status={bundle.status}/></button>):<div className="empty-state small"><div className="empty-icon"><Package size={20}/></div><strong>Még nincs csomagterv</strong><span>Válassz ki több feladatlapot a csomag összeállításához.</span></div>}<div className="bundle-note"><ShieldCheck size={16}/><span>Ez egy tartalmi csomagterv. Rendelés, árazás, készlet és fizetés későbbi fejlesztési ütem.</span></div></div></div></>
}

function ComingSoon({ title, eyebrow, copy, icon }: { title:string; eyebrow:string; copy:string; icon:ReactNode }) { return <><div className="page-heading"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>Az ajánlatok előkészítése egy helyen.</p></div></div><div className="coming-card"><div className="coming-icon">{icon}</div><span className="eyebrow">ELŐKÉSZÍTÉS ALATT</span><h2>Hamarosan összeállíthatod<br/>a saját csomagjaidat.</h2><p>{copy}</p><div className="coming-steps"><span><i>01</i> Válaszd ki a feladatlapokat</span><span><i>02</i> Add meg a digitális vagy fizikai formát</span><span><i>03</i> Készíts belőle ajánlatot</span></div></div></> }

type ProductMaterial = { material_id:string;name:string;unit_name:string;quantity:number;unit_cost:number;total_cost:number }
type SaleProduct = { id:string; worksheet_id?:string; bundle_id?:string; content_title:string; variant_name:string; delivery_type:'DIGITAL'|'PHYSICAL'; list_price:number; material_cost:number; print_sides:number; printing_cost:number; manufacturing_cost:number; active:boolean; units_sold:number; sale_count:number; revenue:number; sold_material_cost:number; materials:ProductMaterial[] }
type SaleRecord = { id:string; sale_date:string; channel:string; list_total:number; agreed_total:number; note?:string; packaging_material_id?:string;packaging_name?:string;packaging_unit?:string;packaging_quantity:number;packaging_unit_cost:number;packaging_total_cost:number; items:{product_id:string;product_title:string;variant_name:string;quantity:number;list_unit_price:number;material_unit_cost:number;packaging_cost:number;agreed_line_total:number}[] }
type SalesSummary = { sale_count:number; revenue:number; material_cost:number;printing_cost:number;packaging_cost:number }
type MaterialItem = { id:string;name:string;unit_name:string;purchased_quantity:number;total_spent:number;unit_cost:number|null;active:boolean;is_packaging:boolean }
type MaterialPurchase = { id:string;material_id:string;material_name:string;unit_name:string;purchase_date:string;quantity:number;total_cost:number;unit_cost:number;note?:string }
type ExpenseRecord = { id:string;category:'EQUIPMENT'|'OTHER';title:string;expense_date:string;amount:number;note?:string }
type ExpenseSummary = { equipment_total:number;other_total:number;material_total:number;all_total:number }

function FinanceView({ credentials, worksheets, bundles, target, initialTab, onTargetHandled, onError, notify }: { credentials:Credentials; worksheets:Worksheet[]; bundles:BundleItem[]; target:{worksheetId:string;productId?:string}|null; initialTab:'overview'|'sales'; onTargetHandled:()=>void; onError:(message:string)=>void; notify:(message:string)=>void }) {
  const [financeTab,setFinanceTab]=useState<'overview'|'equipment'|'materials'|'sales'>(initialTab)
  const [products,setProducts]=useState<SaleProduct[]>([])
  const [productsReady,setProductsReady]=useState(false)
  const [sales,setSales]=useState<SaleRecord[]>([])
  const [summary,setSummary]=useState<SalesSummary>({sale_count:0,revenue:0,material_cost:0,printing_cost:0,packaging_cost:0})
  const [salesFrom,setSalesFrom]=useState(`${new Date().getFullYear()}-${String(new Date().getMonth()+1).padStart(2,'0')}-01`)
  const [salesTo,setSalesTo]=useState(new Date().toLocaleDateString('sv-SE'))
  const [materials,setMaterials]=useState<MaterialItem[]>([])
  const [purchases,setPurchases]=useState<MaterialPurchase[]>([])
  const [expenses,setExpenses]=useState<ExpenseRecord[]>([])
  const [expenseSummary,setExpenseSummary]=useState<ExpenseSummary>({equipment_total:0,other_total:0,material_total:0,all_total:0})
  const [printSideCost,setPrintSideCost]=useState('10.00')
  const [savingPrintSettings,setSavingPrintSettings]=useState(false)
  const [materialName,setMaterialName]=useState('')
  const [materialUnit,setMaterialUnit]=useState('db')
  const [materialIsPackaging,setMaterialIsPackaging]=useState(false)
  const [purchaseMaterial,setPurchaseMaterial]=useState('')
  const [purchaseQuantity,setPurchaseQuantity]=useState('')
  const [purchaseTotal,setPurchaseTotal]=useState('')
  const [purchaseDate,setPurchaseDate]=useState(localDateInputValue())
  const [expenseCategory,setExpenseCategory]=useState<'EQUIPMENT'|'OTHER'>('EQUIPMENT')
  const [expenseTitle,setExpenseTitle]=useState('')
  const [expenseAmount,setExpenseAmount]=useState('')
  const [expenseDate,setExpenseDate]=useState(localDateInputValue())
  const [source,setSource]=useState('')
  const [variant,setVariant]=useState('')
  const [deliveryType,setDeliveryType]=useState<'DIGITAL'|'PHYSICAL'>('PHYSICAL')
  const [printSides,setPrintSides]=useState<1|2>(1)
  const [listPrice,setListPrice]=useState('')
  const [materialCost,setMaterialCost]=useState('0')
  const [productRecipe,setProductRecipe]=useState<{materialId:string;quantity:number}[]>([])
  const [recipeMaterial,setRecipeMaterial]=useState('')
  const [recipeQuantity,setRecipeQuantity]=useState('1')
  const [editingProductId,setEditingProductId]=useState<string|null>(null)
  const [showProductEditor,setShowProductEditor]=useState(false)
  const [adding,setAdding]=useState(false)
  const [saleLines,setSaleLines]=useState<{productId:string;quantity:number}[]>([])
  const [chosenProduct,setChosenProduct]=useState('')
  const [agreedTotal,setAgreedTotal]=useState('')
  const [agreedTouched,setAgreedTouched]=useState(false)
  const [channel,setChannel]=useState('Vinted')
  const [saleDate,setSaleDate]=useState(localDateInputValue())
  const [editingSaleId,setEditingSaleId]=useState<string|null>(null)
  const [packagingMaterialId,setPackagingMaterialId]=useState('')
  const [packagingQuantity,setPackagingQuantity]=useState('1')
  const [note,setNote]=useState('')
  const [savingSale,setSavingSale]=useState(false)
  const load=useCallback(async()=>{
    const [productData,saleData,summaryData,materialData,purchaseData,expenseData,expenseSummaryData,printingData]=await Promise.all([api<SaleProduct[]>(credentials,'/products'),api<SaleRecord[]>(credentials,'/sales'),api<SalesSummary>(credentials,`/sales/summary?from=${salesFrom}&to=${salesTo}`),api<MaterialItem[]>(credentials,'/materials'),api<MaterialPurchase[]>(credentials,'/materials/purchases'),api<ExpenseRecord[]>(credentials,'/expenses'),api<ExpenseSummary>(credentials,'/expenses/summary'),api<{side_cost:string}>(credentials,'/settings/printing')])
    setProducts(productData);setProductsReady(true);setSales(saleData);setSummary(summaryData);setMaterials(materialData);setPurchases(purchaseData);setExpenses(expenseData);setExpenseSummary(expenseSummaryData);setPrintSideCost(String(printingData.side_cost))
  },[credentials,salesFrom,salesTo])
  useEffect(()=>{load().catch((e:Error)=>onError(e.message))},[load,onError])
  useEffect(()=>{setFinanceTab(initialTab)},[initialTab])
  useEffect(()=>{
    if(!target||!productsReady)return
    setFinanceTab('sales')
    if(target.productId){
      const product=products.find((item)=>item.id===target.productId)
      if(product)editProduct(product)
      else onError('A feladatlapon kiválasztott eladható változat nem található.')
    }else{
      clearProductForm();setSource(`WORKSHEET:${target.worksheetId}`);setShowProductEditor(true)
    }
    onTargetHandled()
  },[target,productsReady,products])
  const listTotal=saleLines.reduce((sum,line)=>sum+(products.find((p)=>p.id===line.productId)?.list_price??0)*line.quantity,0)
  const selectedMaterialCost=saleLines.reduce((sum,line)=>sum+Number(products.find((product)=>product.id===line.productId)?.material_cost??0)*line.quantity,0)
  useEffect(()=>{if(!agreedTouched)setAgreedTotal(listTotal.toFixed(2))},[listTotal,agreedTouched])
  const currency=(value:number)=>new Intl.NumberFormat('hu-HU',{style:'currency',currency:'HUF',maximumFractionDigits:2}).format(value)
  const saleProfit=(sale:SaleRecord)=>Number(sale.agreed_total)-(sale.items??[]).reduce((sum,item)=>sum+Number(item.quantity)*Number(item.material_unit_cost)+Number(item.packaging_cost??0),0)
  const selectedSales=sales.filter((sale)=>sale.sale_date>=salesFrom&&sale.sale_date<=salesTo)
  const totalRevenue=selectedSales.reduce((sum,sale)=>sum+Number(sale.agreed_total),0)
  const totalMaterial=Number(summary.material_cost)
  const totalProfit=selectedSales.reduce((sum,sale)=>sum+saleProfit(sale),0)
  const totalConsumablePurchases=purchases.filter((purchase)=>purchase.purchase_date>=salesFrom&&purchase.purchase_date<=salesTo).reduce((sum,purchase)=>sum+Number(purchase.total_cost),0)
  const actualProfit=totalRevenue-totalConsumablePurchases
  const purchaseUnitCost=Number(purchaseQuantity)>0?Number(purchaseTotal)/Number(purchaseQuantity):0
  const recipeEstimatedCost=Math.round((productRecipe.reduce((sum,line)=>sum+line.quantity*Number(materials.find((m)=>m.id===line.materialId)?.unit_cost??0),0)+Number.EPSILON)*100)/100
  const selectedWorksheet=source.startsWith('WORKSHEET:')?worksheets.find((item)=>item.id===source.split(':')[1]):undefined
  const selectedContentPages=selectedWorksheet?Number(selectedWorksheet.total_pages??selectedWorksheet.totalPages??selectedWorksheet.pages?.reduce((sum,page)=>sum+Number(page.page_count||1),0)??0):source.startsWith('BUNDLE:')?(bundles.find((item)=>item.id===source.split(':')[1])?.worksheetIds??[]).reduce((total,id)=>total+Number(worksheets.find((item)=>item.id===id)?.total_pages??worksheets.find((item)=>item.id===id)?.totalPages??0),0):0
  const estimatedSheets=deliveryType==='PHYSICAL'?Math.ceil(selectedContentPages/printSides):0
  const estimatedSurfaces=estimatedSheets*printSides
  const estimatedPrintingCost=Math.round(Number(printSideCost)*estimatedSurfaces*100)/100
  const enteredMaterialCost=productRecipe.length?recipeEstimatedCost:Number(materialCost)||0
  const selectedPackaging=materials.find((material)=>material.id===packagingMaterialId)
  const packagingEstimatedCost=selectedPackaging?Math.round(Number(packagingQuantity||0)*Number(selectedPackaging.unit_cost??0)*100)/100:0
  async function createProduct(event:FormEvent){
    event.preventDefault();if(!source)return
    setAdding(true)
    try{
      const [contentType,contentId]=source.split(':')
      await api(credentials,editingProductId?`/products/${editingProductId}`:'/products',{method:editingProductId?'PUT':'POST',body:JSON.stringify({contentType,contentId,variantName:variant,deliveryType,printSides:deliveryType==='DIGITAL'?1:printSides,listPrice,materialCost:deliveryType==='DIGITAL'?0:productRecipe.length?recipeEstimatedCost:materialCost,materials:deliveryType==='DIGITAL'?[]:productRecipe})})
      clearProductForm();await load();notify('Eladható termék mentve')
    }catch(e){onError((e as Error).message)}finally{setAdding(false)}
  }
  function clearProductForm(){setSource('');setVariant('');setDeliveryType('PHYSICAL');setPrintSides(1);setListPrice('');setMaterialCost('0');setProductRecipe([]);setRecipeMaterial('');setRecipeQuantity('1');setEditingProductId(null);setShowProductEditor(false)}
  function newProduct(){clearProductForm();setShowProductEditor(true)}
  function editProduct(product:SaleProduct){setEditingProductId(product.id);setSource(product.worksheet_id?`WORKSHEET:${product.worksheet_id}`:`BUNDLE:${product.bundle_id}`);setVariant(product.variant_name);setDeliveryType(product.delivery_type??'PHYSICAL');setPrintSides(product.print_sides===2?2:1);setListPrice(String(product.list_price));setMaterialCost(String(product.material_cost));setProductRecipe((product.materials??[]).map((item)=>({materialId:item.material_id,quantity:Number(item.quantity)})));setShowProductEditor(true);setFinanceTab('sales')}
  function addRecipeMaterial(){if(!recipeMaterial)return;setProductRecipe((current)=>{const existing=current.find((line)=>line.materialId===recipeMaterial);return existing?current.map((line)=>line.materialId===recipeMaterial?{...line,quantity:line.quantity+Number(recipeQuantity)}:line):[...current,{materialId:recipeMaterial,quantity:Number(recipeQuantity)}]});setRecipeMaterial('')}
  function addSaleProduct(){if(!chosenProduct)return;setSaleLines((current)=>{const existing=current.find((line)=>line.productId===chosenProduct);return existing?current.map((line)=>line.productId===chosenProduct?{...line,quantity:line.quantity+1}:line):[...current,{productId:chosenProduct,quantity:1}]});setChosenProduct('')}
  async function createSale(event:FormEvent){
    event.preventDefault();if(!saleLines.length)return
    setSavingSale(true)
    try{
      await api(credentials,editingSaleId?`/sales/${editingSaleId}`:'/sales',{method:editingSaleId?'PUT':'POST',body:JSON.stringify({items:saleLines,agreedTotal,saleDate,channel,note,packaging:packagingMaterialId?{materialId:packagingMaterialId,quantity:packagingQuantity}:null})})
      clearSaleForm();await load();notify(editingSaleId?'Eladás frissítve':'Eladás rögzítve')
    }catch(e){onError((e as Error).message)}finally{setSavingSale(false)}
  }
  function clearSaleForm(){setEditingSaleId(null);setSaleLines([]);setAgreedTotal('');setAgreedTouched(false);setSaleDate(localDateInputValue());setChannel('Vinted');setNote('');setPackagingMaterialId('');setPackagingQuantity('1')}
  function editSale(sale:SaleRecord){setEditingSaleId(sale.id);setSaleLines(sale.items.map((item)=>({productId:item.product_id,quantity:Number(item.quantity)})));setAgreedTotal(String(sale.agreed_total));setAgreedTouched(true);setSaleDate(saleDateInputValue(sale.sale_date));setChannel(sale.channel);setNote(sale.note??'');setPackagingMaterialId(sale.packaging_material_id??'');setPackagingQuantity(String(sale.packaging_quantity||1));setFinanceTab('sales');setShowProductEditor(false)}
  async function createMaterial(event:FormEvent){event.preventDefault();try{await api(credentials,'/materials',{method:'POST',body:JSON.stringify({name:materialName,unitName:materialUnit,isPackaging:materialIsPackaging})});setMaterialName('');setMaterialIsPackaging(false);await load();notify('Anyag mentve')}catch(e){onError((e as Error).message)}}
  async function markMaterialPackaging(materialId:string,isPackaging:boolean){try{await api(credentials,`/materials/${materialId}/packaging`,{method:'PUT',body:JSON.stringify({isPackaging})});await load();notify(isPackaging?'Csomagolóanyagként megjelölve':'Jelölés eltávolítva')}catch(e){onError((e as Error).message)}}
  async function createPurchase(event:FormEvent){event.preventDefault();if(!purchaseMaterial)return;try{await api(credentials,`/materials/${purchaseMaterial}/purchases`,{method:'POST',body:JSON.stringify({quantity:purchaseQuantity,totalCost:purchaseTotal,purchaseDate})});setPurchaseQuantity('');setPurchaseTotal('');await load();notify('Beszerzés rögzítve')}catch(e){onError((e as Error).message)}}
  async function createExpense(event:FormEvent){event.preventDefault();try{await api(credentials,'/expenses',{method:'POST',body:JSON.stringify({category:expenseCategory,title:expenseTitle,amount:expenseAmount,expenseDate})});setExpenseTitle('');setExpenseAmount('');await load();notify('Kiadás rögzítve')}catch(e){onError((e as Error).message)}}
  async function savePrintSettings(event:FormEvent){event.preventDefault();setSavingPrintSettings(true);try{const saved=await api<{side_cost:string}>(credentials,'/settings/printing',{method:'PUT',body:JSON.stringify({sideCost:printSideCost})});setPrintSideCost(String(saved.side_cost));await load();notify('Nyomtatási díj mentve')}catch(e){onError((e as Error).message)}finally{setSavingPrintSettings(false)}}
  return <>
    <div className="page-heading"><div><span className="eyebrow">SAJÁT ÉRTÉKESÍTÉS</span><h1>Pénzügy</h1><p>Válassz nézetet a kiadások, anyagok vagy eladások kezeléséhez.</p></div></div>
    <div className="finance-tabs" role="tablist" aria-label="Pénzügyi nézetek">
      {([['overview','Áttekintő'],['equipment','Egyszeri kiadások'],['materials','Fogyóanyagok'],['sales','Eladások']] as const).map(([id,label])=><button key={id} type="button" role="tab" aria-selected={financeTab===id} className={financeTab===id?'active':''} onClick={()=>setFinanceTab(id)}>{label}</button>)}
    </div>
    <div className="finance-period"><strong>Eladási időszak</strong><label className="field-label">Kezdete<input type="date" value={salesFrom} max={salesTo} onChange={(e)=>setSalesFrom(e.target.value)}/></label><label className="field-label">Vége<input type="date" value={salesTo} min={salesFrom} onChange={(e)=>setSalesTo(e.target.value)}/></label></div>
    {financeTab==='overview'&&<>
      <div className="stats-grid finance-stats"><StatCard icon={<Coins size={18}/>} tone="sage" label="Teljes bevétel" value={currency(totalRevenue)} note={`${selectedSales.length} eladás a kiválasztott időszakban`}/><StatCard icon={<TrendingUp size={18}/>} tone="peach" label="Nyereség" value={currency(totalProfit)} note="az eladásoknál megjelenő nyereségek összege"/><StatCard icon={<Package size={18}/>} tone="yellow" label="Fogyóanyag-beszerzések" value={currency(totalConsumablePurchases)} note="a kiválasztott időszakban"/><StatCard icon={<TrendingUp size={18}/>} tone="sage" label="Tényleges nyereség" value={currency(actualProfit)} note="teljes bevétel mínusz fogyóanyag-beszerzések"/></div>
      <section className="form-card finance-recent-overview"><div className="section-title-row"><div><span className="eyebrow">ELADÁSOK</span><h2>Legutóbbi eladások</h2></div><button type="button" className="text-button" onClick={()=>setFinanceTab('sales')}>Eladások megnyitása <ArrowLeft className="arrow-right" size={14}/></button></div>{sales.slice(0,8).map((sale)=><div className="finance-sale-row finance-sale-record" key={sale.id}><span><strong>{formatSaleDate(sale.sale_date)}</strong><small>{sale.channel} · {sale.items?.length??0} terméksor</small></span><span className="finance-sale-amount"><small>Teljes ár</small><b>{currency(Number(sale.agreed_total))}</b></span><span className="finance-sale-profit-column"><small>Nyereség</small><b className={saleProfit(sale)>=0?"sale-profit":"sale-loss"}>{currency(saleProfit(sale))}</b></span><button type="button" className="text-button" onClick={()=>editSale(sale)}>Szerkesztés</button></div>)}{!sales.length&&<p className="muted-small">Még nincs rögzített eladás.</p>}</section>
    </>}
    {financeTab==='equipment'&&<section className="form-card finance-section"><div className="finance-section-heading"><div><span className="eyebrow">EGYSZERI KIADÁSOK</span><h2>Eszközök és egyéb kiadások</h2><p>Egyszeri vásárlásokat rögzíthetsz itt. Ezek nem keverednek a termék anyagköltségével.</p></div><strong>{currency(Number(expenseSummary.equipment_total)+Number(expenseSummary.other_total))}</strong></div><form className="expense-form expense-form-wide" onSubmit={createExpense}><label className="field-label">Típus<select value={expenseCategory} onChange={(e)=>setExpenseCategory(e.target.value as 'EQUIPMENT'|'OTHER')}><option value="EQUIPMENT">Eszköz</option><option value="OTHER">Egyéb kiadás</option></select></label><label className="field-label">Megnevezés<input required value={expenseTitle} onChange={(e)=>setExpenseTitle(e.target.value)} placeholder="Például: Laminálógép"/></label><label className="field-label">Összeg (Ft)<input required type="number" min="1" step="1" value={expenseAmount} onChange={(e)=>setExpenseAmount(e.target.value)}/></label><label className="field-label">Dátum<input required type="date" value={expenseDate} onChange={(e)=>setExpenseDate(e.target.value)}/></label><button className="button primary"><Plus size={14}/>Kiadás rögzítése</button></form><div className="finance-history"><h3>Rögzített kiadások</h3>{expenses.map((expense)=><div className="finance-sale-row" key={expense.id}><span><strong>{expense.title}</strong><small>{new Date(`${expense.expense_date}T00:00:00`).toLocaleDateString('hu-HU')} · {expense.category==='EQUIPMENT'?'Eszköz':'Egyéb kiadás'}</small></span><b>{currency(Number(expense.amount))}</b></div>)}{!expenses.length&&<p className="muted-small">Még nincs rögzített egyszeri kiadás.</p>}</div></section>}
    {financeTab==='materials'&&<section className="form-card finance-section"><div className="finance-section-heading"><div><span className="eyebrow">FOGYÓANYAGOK</span><h2>Anyagok és beszerzések</h2><p>Az egységár a beszerzések súlyozott átlaga. A felület nem vezet készletet.</p></div><strong>{currency(Number(expenseSummary.material_total))}</strong></div><div className="materials-forms"><form className="expense-form" onSubmit={createMaterial}><h3>Új anyag</h3><label className="field-label">Anyag neve<input required value={materialName} onChange={(e)=>setMaterialName(e.target.value)} placeholder="Például: A4-es papír"/></label><label className="field-label">Mértékegység<input required value={materialUnit} onChange={(e)=>setMaterialUnit(e.target.value)} placeholder="lap, méter, db"/></label><label className="material-packaging-check"><input type="checkbox" checked={materialIsPackaging} onChange={(e)=>setMaterialIsPackaging(e.target.checked)}/><span>Csomagolóanyagként is kiválasztható eladásnál</span></label><button className="button secondary"><Plus size={14}/>Anyag mentése</button></form><form className="expense-form" onSubmit={createPurchase}><h3>Beszerzés rögzítése</h3><label className="field-label">Anyag<select required value={purchaseMaterial} onChange={(e)=>setPurchaseMaterial(e.target.value)}><option value="">Válassz anyagot</option>{materials.map((material)=><option key={material.id} value={material.id}>{material.name} ({material.unit_name})</option>)}</select></label><div className="two-fields"><label className="field-label">Mennyiség<input required type="number" min="0.001" step="0.001" value={purchaseQuantity} onChange={(e)=>setPurchaseQuantity(e.target.value)}/></label><label className="field-label">Teljes ár (Ft)<input required type="number" min="1" step="1" value={purchaseTotal} onChange={(e)=>setPurchaseTotal(e.target.value)}/></label></div><label className="field-label">Beszerzés dátuma<input type="date" value={purchaseDate} onChange={(e)=>setPurchaseDate(e.target.value)}/></label><p className="unit-cost-preview">Egységár: <strong>{currency(purchaseUnitCost)} / {materials.find((m)=>m.id===purchaseMaterial)?.unit_name??'egység'}</strong></p><button className="button primary" disabled={!materials.length}><Plus size={14}/>Beszerzés rögzítése</button></form></div><div className="materials-history"><div className="finance-history"><h3>Anyagok egységárai</h3>{materials.map((material)=><div className="finance-product-row" key={material.id}><span><strong>{material.name}</strong><small>{Number(material.purchased_quantity)} {material.unit_name} beszerzés · {currency(Number(material.total_spent))} összesen</small><label className="material-packaging-check"><input type="checkbox" checked={material.is_packaging} onChange={(e)=>markMaterialPackaging(material.id,e.target.checked)}/><span>Eladásnál választható csomagolóanyag</span></label></span><b>{material.unit_cost?`${currency(Number(material.unit_cost))} / ${material.unit_name}`:'Nincs beszerzés'}</b></div>)}{!materials.length&&<p className="muted-small">Még nincs felvett fogyóanyag.</p>}</div><div className="finance-history"><h3>Beszerzési előzmények</h3>{purchases.map((purchase)=><div className="finance-sale-row" key={purchase.id}><span><strong>{purchase.material_name} · {Number(purchase.quantity)} {purchase.unit_name}</strong><small>{new Date(`${purchase.purchase_date}T00:00:00`).toLocaleDateString('hu-HU')} · {currency(Number(purchase.unit_cost))}/{purchase.unit_name}</small></span><b>{currency(Number(purchase.total_cost))}</b></div>)}{!purchases.length&&<p className="muted-small">Még nincs rögzített beszerzés.</p>}</div></div></section>}
    {financeTab==='sales'&&<div className="finance-layout"><div className="finance-main"><form className="form-card print-settings-card" onSubmit={savePrintSettings}><div><span className="eyebrow">NYOMTATÁS BEÁLLÍTÁSA</span><h2>Nyomtatási alapdíj</h2><p>Ez a tintaköltség egy nyomtatott oldalra. Kétoldalas módban egy papírlap mindkét oldala nyomtatott felületnek számít.</p></div><label className="field-label">Alapértelmezett költség / nyomtatott oldal (Ft)<input required type="number" min="0" step="0.01" value={printSideCost} onChange={(e)=>setPrintSideCost(e.target.value)}/></label><button className="button primary" disabled={savingPrintSettings}>{savingPrintSettings?"Mentés…":"Nyomtatási díj mentése"}</button></form><section className="form-card finance-product-catalog"><div className="section-title-row"><div><span className="eyebrow">TERMÉKBEÁLLÍTÁS</span><h2>Eladható termék hozzáadása</h2><p>A változat a feladatlap vagy csomag egy konkrét kivitele, saját költséggel és eladási árral.</p></div><button type="button" className="button primary" onClick={newProduct}><Plus size={15}/>Eladható termék hozzáadása</button></div>{products.length?<div className="finance-product-grid">{products.map((product)=><button type="button" className={`finance-product-card ${editingProductId===product.id?'selected':''}`} key={product.id} onClick={()=>editProduct(product)}><span className="finance-product-card-main"><strong>{product.content_title}</strong><small>{product.variant_name}</small></span><span className="finance-product-card-cost"><small>Gyártási költség</small><b>{currency(Number(product.manufacturing_cost))}</b></span><span className="finance-product-card-price"><small>Tervezett ár</small><b>{currency(Number(product.list_price))}</b></span><ChevronDown size={16}/></button>)}</div>:<div className="finance-empty-products"><Package size={22}/><strong>Még nincs eladható termékváltozat</strong><span>Hozz létre egyet egy feladatlap vagy csomag összekapcsolásával.</span><button type="button" className="button secondary" onClick={newProduct}>Első termék felvétele</button></div>}</section>
      {showProductEditor&&<form className="form-card product-detail-editor" onSubmit={createProduct}><div className="finance-section-heading"><div><span className="eyebrow">{editingProductId?'TERMÉK ADATLAPJA':'ÚJ ELADHATÓ TERMÉK'}</span><h2>{editingProductId?variant||'Termék szerkesztése':'Termékváltozat beállítása'}</h2><p>Állítsd be a felhasznált anyagokat és a nyomtatási módot. A nyomtatás automatikusan hozzáadódik a teljes gyártási költséghez.</p></div><button type="button" className="icon-button" aria-label="Termék adatlapjának bezárása" onClick={clearProductForm}><X size={17}/></button></div><div className="product-detail-fields"><label className="field-label">Kapcsolódó tartalom<select required value={source} onChange={(e)=>setSource(e.target.value)}><option value="">Válassz feladatlapot vagy csomagot</option><optgroup label="Feladatlapok">{worksheets.filter((item)=>item.status!=='ARCHIVED').map((item)=><option key={item.id} value={`WORKSHEET:${item.id}`}>{item.title}</option>)}</optgroup><optgroup label="Csomagtervek">{bundles.filter((item)=>item.status!=='ARCHIVED').map((item)=><option key={item.id} value={`BUNDLE:${item.id}`}>{item.title}</option>)}</optgroup></select></label><label className="field-label">Változat neve<input required maxLength={160} value={variant} onChange={(e)=>setVariant(e.target.value)} placeholder="Például: Nyomtatott, laminált"/></label><label className="field-label">Kivitel<select value={deliveryType} onChange={(e)=>{const next=e.target.value as 'DIGITAL'|'PHYSICAL';setDeliveryType(next);if(next==='DIGITAL'){setProductRecipe([]);setMaterialCost('0')}}}><option value="PHYSICAL">Fizikai termék</option><option value="DIGITAL">Digitális termék</option></select></label>{deliveryType==="PHYSICAL"&&<label className="field-label">Nyomtatási mód<select value={printSides} onChange={(e)=>setPrintSides(Number(e.target.value) as 1|2)}><option value={1}>Egyoldalas</option><option value={2}>Kétoldalas</option></select></label>}<label className="field-label">Tervezett eladási ár (Ft)<input required type="number" min="1" step="1" value={listPrice} onChange={(e)=>setListPrice(e.target.value)} placeholder="1800"/></label></div>{deliveryType==='PHYSICAL'?<div className="recipe-editor"><h3>Állandó anyagfelhasználás · egy termékhez</h3><p className="finance-hint">Ezt egyszer kell beállítani: minden ilyen fizikai termék ugyanennyi fogyóanyagot használ. Az anyagköltség a beszerzési árak változásakor frissül.</p><div className="recipe-add-row"><label className="field-label">Felvitt fogyóanyag<select value={recipeMaterial} onChange={(e)=>setRecipeMaterial(e.target.value)}><option value="">Válassz fogyóanyagot</option>{materials.map((material)=><option key={material.id} value={material.id}>{material.name} · {currency(Number(material.unit_cost??0))}/{material.unit_name}</option>)}</select></label><label className="field-label recipe-quantity">Felhasznált mennyiség<input type="number" min="0.001" step="0.001" value={recipeQuantity} onChange={(e)=>setRecipeQuantity(e.target.value)}/></label><button type="button" className="button secondary" disabled={!recipeMaterial} onClick={addRecipeMaterial}><Plus size={14}/>Hozzáadás</button></div>{productRecipe.length>0?<div className="recipe-lines">{productRecipe.map((line)=><div className="recipe-line" key={line.materialId}><span><strong>{materials.find((m)=>m.id===line.materialId)?.name}</strong><small>{currency(Number(materials.find((m)=>m.id===line.materialId)?.unit_cost??0))} / {materials.find((m)=>m.id===line.materialId)?.unit_name}{Number(materials.find((m)=>m.id===line.materialId)?.unit_cost??0)===0?' · nincs beszerzési ár':''}</small></span><label className="field-label recipe-quantity">Mennyiség<input type="number" min="0.001" step="0.001" value={line.quantity} onChange={(e)=>setProductRecipe((current)=>current.map((item)=>item.materialId===line.materialId?{...item,quantity:Math.max(.001,Number(e.target.value)||.001)}:item))}/></label><strong>{currency(line.quantity*Number(materials.find((m)=>m.id===line.materialId)?.unit_cost??0))}</strong><button type="button" className="icon-button" aria-label="Fogyóanyag eltávolítása" onClick={()=>setProductRecipe((current)=>current.filter((item)=>item.materialId!==line.materialId))}><X size={15}/></button></div>)}</div>:<p className="recipe-empty">Még nincs kiválasztva fogyóanyag. Digitális vagy anyag nélküli terméknél ez lehet üres.</p>}{!productRecipe.length&&<label className="field-label manual-cost-field">Kézzel megadott anyagköltség (Ft, legfeljebb 2 tizedes)<input type="number" min="0" step="0.01" value={materialCost} onChange={(e)=>setMaterialCost(e.target.value)}/></label>}<div className="product-cost-summary"><div><small>Anyagköltség / db</small><strong>{currency(enteredMaterialCost)}</strong></div><div><small>Tervezett teljes költség / db</small><strong>{currency(enteredMaterialCost+estimatedPrintingCost)}</strong><small className="cost-breakdown">Nyomtatás: {currency(estimatedPrintingCost)} · {estimatedSurfaces} nyomtatott oldal</small></div><div><small>Tervezett nyereség / db</small><strong>{currency((Number(listPrice)||0)-enteredMaterialCost-estimatedPrintingCost)}</strong></div></div><p className="finance-hint">Nyomtatás: {estimatedSheets} papírlap × {printSides} oldal × {currency(Number(printSideCost))} = {currency(estimatedPrintingCost)} / db ({estimatedSurfaces} nyomtatott oldal). A korábbi eladások költségadata nem változik.</p></div>:<div className="recipe-editor"><h3>Digitális termék</h3><p className="recipe-empty">Digitális terméknél nincs fogyóanyag és anyagköltség.</p><div className="product-cost-summary"><div><small>Anyagköltség / db</small><strong>{currency(0)}</strong></div><div><small>Tervezett teljes költség / db</small><strong>{currency(0)}</strong></div><div><small>Tervezett nyereség / db</small><strong>{currency(Number(listPrice)||0)}</strong></div></div></div>}<div className="finance-product-actions"><button className="button primary" disabled={adding||!source}><Check size={15}/>{adding?'Mentés…':editingProductId?'Termékváltozat mentése':'Termékváltozat létrehozása'}</button><button type="button" className="button secondary" onClick={clearProductForm}>Mégse</button></div></form>}
      {!showProductEditor&&<form className="form-card finance-form" onSubmit={createSale}><div className="form-card-heading"><div className="step-number"><Receipt size={16}/></div><div><h2>{editingSaleId?"Eladás szerkesztése":"Új eladás"}</h2><p>Több terméket is rögzíthetsz; csomagolóanyag költsége is hozzáadható.</p></div></div><div className="sale-add-row"><label className="field-label">Termék<select value={chosenProduct} onChange={(e)=>setChosenProduct(e.target.value)}><option value="">Termék hozzáadása</option>{products.filter((product)=>product.active).map((product)=><option key={product.id} value={product.id}>{product.content_title} · {product.variant_name} · {currency(Number(product.list_price))}</option>)}</select></label><button className="button secondary" type="button" disabled={!chosenProduct} onClick={addSaleProduct}><Plus size={15}/>Hozzáadás</button></div>{saleLines.length>0&&<div className="sale-line-list">{saleLines.map((line)=><div className="sale-line" key={line.productId}><span><strong>{products.find((p)=>p.id===line.productId)?.content_title}</strong><small>{products.find((p)=>p.id===line.productId)?.variant_name} · eladási ár {currency(Number(products.find((p)=>p.id===line.productId)?.list_price??0))}/db</small><small>Anyag {currency(Number(products.find((p)=>p.id===line.productId)?.material_cost??0))} + nyomtatás {currency(Number(products.find((p)=>p.id===line.productId)?.printing_cost??0))} = költség {currency(Number(products.find((p)=>p.id===line.productId)?.manufacturing_cost??0))}/db</small>{(products.find((p)=>p.id===line.productId)?.materials??[]).length>0&&<small>Felhasznált anyagok: {(products.find((p)=>p.id===line.productId)?.materials??[]).map((material)=>`${material.name}: ${Number(material.quantity)} ${material.unit_name}`).join(' · ')}</small>}<strong className="sale-line-material-total">Teljes anyagköltség: {currency(Number(products.find((p)=>p.id===line.productId)?.material_cost??0)*line.quantity)}</strong></span><label className="field-label">Db<input type="number" min="1" max="1000" value={line.quantity} onChange={(e)=>setSaleLines((current)=>current.map((item)=>item.productId===line.productId?{...item,quantity:Math.max(1,Number(e.target.value)||1)}:item))}/></label><strong>{currency(Number(products.find((p)=>p.id===line.productId)?.list_price??0)*line.quantity)}</strong><button type="button" className="icon-button" aria-label="Terméksor törlése" onClick={()=>setSaleLines((current)=>current.filter((item)=>item.productId!==line.productId))}><X size={15}/></button></div>)}</div>}{!saleLines.length&&<p className="sale-empty-hint">Még nem választottál terméket az eladáshoz.</p>}<div className="sale-totals"><span>Listaárak összege</span><strong>{currency(listTotal)}</strong></div>{saleLines.length>0&&<div className="sale-totals sale-material-total-summary"><span>Összes anyagköltség</span><strong>{currency(selectedMaterialCost)}</strong></div>}<div className="two-fields"><label className="field-label">Megbeszélt végösszeg (Ft)<input required type="number" min="0" step="1" value={agreedTotal} onChange={(e)=>{setAgreedTotal(e.target.value);setAgreedTouched(true)}}/></label><label className="field-label">Dátum<input type="date" required value={saleDate} onChange={(e)=>setSaleDate(e.target.value)}/></label></div><div className="two-fields"><label className="field-label">Csomagolóanyag (opcionális)<select value={packagingMaterialId} onChange={(e)=>setPackagingMaterialId(e.target.value)}><option value="">Nincs csomagolóanyag</option>{materials.filter((material)=>material.active&&material.is_packaging).map((material)=><option key={material.id} value={material.id}>{material.name} · {currency(Number(material.unit_cost??0))}/{material.unit_name}</option>)}</select></label><label className="field-label">Felhasznált mennyiség<input type="number" min="0.001" step="0.001" disabled={!packagingMaterialId} value={packagingQuantity} onChange={(e)=>setPackagingQuantity(e.target.value)}/></label></div>{!materials.some((material)=>material.active&&material.is_packaging)&&<p className="finance-hint">Csomagolóanyag hozzáadásához vagy megjelöléséhez menj a Pénzügy → Fogyóanyagok nézetbe.</p>}{packagingMaterialId&&<p className="finance-hint">Becsült csomagolási költség: {currency(packagingEstimatedCost)}. Az eladás mentésekor ez költségpillanatképként rögzül.</p>}<div className="two-fields"><label className="field-label">Értékesítési csatorna<input required value={channel} onChange={(e)=>setChannel(e.target.value)} placeholder="Vinted"/></label><label className="field-label">Megjegyzés<input value={note} onChange={(e)=>setNote(e.target.value)} placeholder="Opcionális"/></label></div>{saleLines.length>0&&<p className="finance-hint">Az eltérés automatikusan, arányosan oszlik el a terméksorok között.</p>}<button className="button primary" disabled={savingSale||!saleLines.length||!agreedTotal}><Check size={15}/>{savingSale?'Mentés…':editingSaleId?'Módosítások mentése':'Eladás rögzítése'}</button>{editingSaleId&&<button type="button" className="button secondary" onClick={clearSaleForm}>Szerkesztés megszakítása</button>}<div className="finance-history"><h3>Legutóbbi eladások</h3>{sales.slice(0,10).map((sale)=><div className="finance-sale-row finance-sale-record" key={sale.id}><span><strong>{formatSaleDate(sale.sale_date)}</strong><small>{sale.channel} · {sale.items?.length??0} terméksor</small></span><span className="finance-sale-amount"><small>Teljes ár</small><b>{currency(Number(sale.agreed_total))}</b></span><span className="finance-sale-profit-column"><small>Nyereség</small><b className={saleProfit(sale)>=0?"sale-profit":"sale-loss"}>{currency(saleProfit(sale))}</b></span><button type="button" className="text-button" onClick={()=>editSale(sale)}>Szerkesztés</button></div>)}{!sales.length&&<p className="muted-small">Még nincs rögzített eladás.</p>}</div></form>}
      </div><aside className="finance-products finance-sales-summary"><h2>Eladási összesítő</h2><StatCard icon={<Coins size={18}/>} tone="sage" label="Bevétel" value={currency(totalRevenue)} note={`${selectedSales.length} rögzített eladás`}/><StatCard icon={<TrendingUp size={18}/>} tone="peach" label="Nyereség" value={currency(totalProfit)} note="a legutóbbi eladásoknál megjelenő nyereségek összege"/><StatCard icon={<TrendingUp size={18}/>} tone="yellow" label="Becsült gyártási költség" value={currency(totalMaterial)} note="eladott termékek alapján"/></aside></div>}
  </>
}
