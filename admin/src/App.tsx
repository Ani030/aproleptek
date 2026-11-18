import { useCallback, useEffect, useMemo, useState, type ComponentType, type Dispatch, type FormEvent, type ReactNode, type SetStateAction } from 'react'
import {
  Activity, ArrowDown, ArrowLeft, ArrowUp, BookOpen, Check, CircleHelp,
  ClipboardList, FileImage, FilePlus2, Files, Flower2, Home, LogOut, MoreHorizontal, Printer,
  Package, Plus, Search, Settings2, ShieldCheck, Sparkles, Upload, X,
} from 'lucide-react'

type Credentials = { username: string; password: string }
type Term = { id: string; term_type: 'AGE' | 'TASK_TYPE' | 'TOPIC' | 'SKILL'; name: string; slug: string }
type Page = {
  id?: string; page_type: 'MAIN' | 'CUTOUT'; title?: string; sort_order: number; page_count: number
  paper_size: string; orientation: string; color_mode: string; notes?: string
  preview_asset_id?: string; preview_name?: string; preview_media_type?: string; print_asset_id?: string; print_name?: string; print_media_type?: string
}
type Worksheet = {
  id: string; title: string; slug: string; summary?: string; description?: string; instructions?: string
  status: string; paper_size: string; orientation: string; color_mode: string; usage_type: string
  safety_notes?: string; preview_asset_id?: string; pages?: Page[]; terms?: Term[]; totalPages?: number; total_pages?: number
}
type ActivityItem = { id: string; title: string; summary?: string; status: string; duration_minutes?: number }
type ActivityForm = { id: string; title: string; summary: string; durationMinutes: string; instructions: string; materials:string; preparation:string; easyVariant:string; challengeVariant:string; safetyNotes:string; locationType:string; status: string; termIds:string[] }
type BundleItem = { id:string; title:string; description?:string; bundle_type:string; status:string; worksheet_count?:number; worksheetIds?:string[] }
type BundleForm = { id:string; title:string; slug:string; description:string; bundleType:string; status:string; worksheetIds:string[] }
type Dashboard = { worksheets: number; activities: number; drafts: number; recentWorksheets: Worksheet[] }
type Section = 'overview' | 'worksheets' | 'activities' | 'bundles' | 'taxonomy'

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'
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
  const [query, setQuery] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [form, setForm] = useState<Worksheet>(blankWorksheet())
  const [activityForm, setActivityForm] = useState<ActivityForm>({ id: '', title: '', summary: '', durationMinutes: '', instructions: '', materials:'',preparation:'',easyVariant:'',challengeVariant:'',safetyNotes:'',locationType:'ANY',status: 'DRAFT', termIds:[] })
  const [bundleForm, setBundleForm] = useState<BundleForm>({ id:'',title:'',slug:'',description:'',bundleType:'DIGITAL',status:'DRAFT',worksheetIds:[] })
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')
  const [error, setError] = useState('')

  const loadDashboard = useCallback(async (auth: Credentials) => {
    const [data, worksheetData, activityData, taxonomy, bundleData] = await Promise.all([
      api<Dashboard>(auth, '/dashboard'), api<{ items: Worksheet[] }>(auth, '/worksheets?size=100'),
      api<{ items: ActivityItem[] }>(auth, '/activities?size=100'), api<Term[]>(auth, '/taxonomy'), api<BundleItem[]>(auth,'/bundles'),
    ])
    setDashboard(data); setWorksheets(worksheetData.items); setActivities(activityData.items); setTerms(taxonomy); setBundles(bundleData)
    setError('')
  }, [])

  useEffect(() => {
    if (!credentials) return
    loadDashboard(credentials).catch((e: Error) => { setError(e.message); if (e.message.includes('belépési')) setCredentials(null) })
  }, [credentials, loadDashboard])

  const notify = (message: string) => { setToast(message); window.setTimeout(() => setToast(''), 3000) }
  const visibleWorksheets = useMemo(() => worksheets.filter((item) => {
    const q = query.trim().toLocaleLowerCase('hu')
    return !q || item.title.toLocaleLowerCase('hu').includes(q)
  }), [worksheets, query])

  async function login(event: FormEvent) {
    event.preventDefault(); setLoginError('')
    const auth = { username, password }
    try { await api(auth, '/auth/me'); setCredentials(auth); setPassword('') }
    catch (e) { setLoginError((e as Error).message) }
  }

  function openNewWorksheet() { setSelectedWorksheet(null); setEditingId(null); setEditorOpen(true); setForm(blankWorksheet()); setSection('worksheets') }
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
    try {
      const uploadedFiles = await Promise.all(files.map(async (file) => {
        const data = new FormData(); data.append('file', file)
        return api<{ id: string; originalName: string; mediaType: string }>(credentials, '/media', { method: 'POST', body: data })
      }))
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
    { id: 'activities', label: 'Játékötletek', icon: Sparkles }, { id: 'bundles', label: 'Csomagtervek', icon: Package }, { id: 'taxonomy', label: 'Kategóriák', icon: Settings2 },
  ]

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand-lockup"><div className="brand-mark"><Flower2 size={21} strokeWidth={1.8} /></div><div><strong>apróLéptek</strong><span>MŰHELY · ADMIN</span></div></div>
      <div className="side-label">MUNKATERÜLET</div>
      <nav>{nav.map(({ id, label, icon: Icon }) => <button key={id} className={`nav-item ${section === id ? 'active' : ''}`} onClick={() => { setSection(id); setSelectedWorksheet(null); setEditingId(null); setEditorOpen(false); setError('') }}><Icon size={18} /><span>{label}</span>{id === 'worksheets' && <span className="nav-count">{worksheets.length}</span>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="help-card"><div className="help-icon"><CircleHelp size={17} /></div><strong>Segítségre van szükséged?</strong><span>A feladatlapok rendszerezése itt kezdődik.</span></div><div className="profile"><div className="avatar">A</div><div className="profile-name"><strong>{credentials.username}</strong><span>Adminisztrátor</span></div><button className="icon-button" title="Kijelentkezés" onClick={() => { setCredentials(null); setDashboard(null) }}><LogOut size={17} /></button></div></div>
    </aside>
    <main className="main-area">
      <header className="topbar"><div className="breadcrumbs"><span>Műhely</span><span className="crumb-slash">/</span><strong>{nav.find((n) => n.id === section)?.label}</strong></div><div className="topbar-right"><span className="secure-label"><ShieldCheck size={15} /> Biztonságos admin</span><div className="topbar-avatar">A</div></div></header>
      <div className="page-content">
        {error && <div className="alert"><span>{error}</span><button onClick={() => setError('')}><X size={16} /></button></div>}
        {section === 'overview' && <Overview dashboard={dashboard} onNew={openNewWorksheet} onNavigate={setSection} onOpen={openWorksheet} />}
        {section === 'worksheets' && <>
          {editorOpen
            ? <WorksheetEditor form={form} setForm={setForm} editing={!!editingId} terms={terms} credentials={credentials} busy={busy} onSave={saveWorksheet} onCancel={() => { setEditorOpen(false); setForm(blankWorksheet()); if (!editingId) setSelectedWorksheet(null) }} onUpload={uploadFile} />
            : selectedWorksheet
              ? <WorksheetOverview key={selectedWorksheet.id} worksheet={selectedWorksheet} credentials={credentials} onBack={() => setSelectedWorksheet(null)} onEdit={editSelectedWorksheet} />
              : <WorksheetList items={visibleWorksheets} query={query} credentials={credentials} onQuery={setQuery} onNew={openNewWorksheet} onOpen={openWorksheet} />}
        </>}
        {section === 'activities' && <ActivityView items={activities} form={activityForm} setForm={setActivityForm} terms={terms} busy={busy} onSave={saveActivity} onEdit={editActivity} />}
        {section === 'bundles' && <BundleView bundles={bundles} worksheets={worksheets} form={bundleForm} setForm={setBundleForm} busy={busy} onSave={saveBundle} onEdit={editBundle} />}
        {section === 'taxonomy' && <TaxonomyView terms={terms} credentials={credentials} reload={() => loadDashboard(credentials)} notify={notify} />}
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

function WorksheetOverview({ worksheet, credentials, onBack, onEdit }: { worksheet: Worksheet; credentials: Credentials; onBack: () => void; onEdit: () => void }) {
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
  const selectedImageIds = images.filter((file) => selectedIds.includes(file.id)).map((file) => file.id)
  const labels: Record<string, string> = { DRAFT: 'Piszkozat', REVIEW: 'Ellenőrzésre vár', PUBLISHED: 'Publikált', ARCHIVED: 'Archivált' }
  const usage: Record<string, string> = { DIGITAL: 'Digitális', PHYSICAL: 'Fizikai', BOTH: 'Digitális és fizikai' }
  return <>
    <div className="worksheet-detail-heading"><div><button className="back-link" onClick={onBack}><ArrowLeft size={15}/> Vissza a feladatlapokhoz</button><span className="eyebrow">FELADATLAP ÁTTEKINTÉSE</span><h1>{worksheet.title}</h1></div><button className="button primary" onClick={onEdit}><Settings2 size={15}/> Szerkesztés</button></div>
    {images.length > 0 && <section className="worksheet-detail-gallery"><div className="worksheet-detail-section-heading"><div><span className="eyebrow">KÉPEK</span><h2>Nyomtatásra kész képek</h2><p>Jelöld ki, mely képeket szeretnéd nyomtatni.</p></div><span>{selectedImageIds.length} / {images.length} kijelölve</span></div><div className="detail-print-actions"><button className="text-button" onClick={() => setSelectedIds(images.map((file) => file.id))}>Összes kijelölése</button><button className="text-button muted" onClick={() => setSelectedIds([])}>Kijelölés törlése</button><button className="button secondary" onClick={() => printImageAssets(credentials, images.map((file) => file.id))}><Printer size={15}/> Összes kép nyomtatása</button><button className="button primary" disabled={!selectedImageIds.length} onClick={() => printImageAssets(credentials, selectedImageIds)}><Printer size={15}/> Kijelöltek nyomtatása</button></div><div className="worksheet-detail-images">{images.map((file) => <figure className={`worksheet-detail-image ${selectedIds.includes(file.id) ? 'selected' : ''}`} key={file.id}><button className="detail-image-open" type="button" onClick={() => setPreview(file)} aria-label={`${file.name} előnézete`}><AuthenticatedImage id={file.id} credentials={credentials} alt={file.name} className="detail-image"/><span>Előnézet</span></button><figcaption><label><input type="checkbox" checked={selectedIds.includes(file.id)} onChange={() => setSelectedIds((current) => current.includes(file.id) ? current.filter((id) => id !== file.id) : [...current, file.id])}/><span>{file.name}</span></label></figcaption></figure>)}</div></section>}
    <div className="worksheet-detail-data"><section className="worksheet-data-card"><span className="eyebrow">FELVITT ADATOK</span><h2>{worksheet.title}</h2>{worksheet.summary && <p className="worksheet-lead">{worksheet.summary}</p>}{worksheet.description && <div className="worksheet-data-block"><h3>Leírás</h3><p>{worksheet.description}</p></div>}{worksheet.instructions && <div className="worksheet-data-block"><h3>Útmutató</h3><p>{worksheet.instructions}</p></div>}{worksheet.safety_notes && <div className="worksheet-data-block"><h3>Biztonsági tudnivalók</h3><p>{worksheet.safety_notes}</p></div>}{worksheet.terms?.length ? <div className="worksheet-detail-tags">{worksheet.terms.map((term) => <span key={term.id}>{term.name}</span>)}</div> : null}</section><aside className="worksheet-facts-card"><span className="eyebrow">BEÁLLÍTÁSOK</span><dl><div><dt>Státusz</dt><dd>{labels[worksheet.status] ?? worksheet.status}</dd></div><div><dt>Felhasználás</dt><dd>{usage[worksheet.usage_type] ?? worksheet.usage_type}</dd></div><div><dt>Papírméret</dt><dd>{worksheet.paper_size}</dd></div><div><dt>Tájolás</dt><dd>{worksheet.orientation === 'LANDSCAPE' ? 'Fekvő' : 'Álló'}</dd></div><div><dt>Színmód</dt><dd>{worksheet.color_mode === 'GRAYSCALE' ? 'Fekete-fehér' : 'Színes'}</dd></div><div><dt>Oldalak száma</dt><dd>{worksheet.totalPages ?? worksheet.total_pages ?? worksheet.pages?.reduce((sum, page) => sum + (page.page_count || 1), 0) ?? 0}</dd></div></dl></aside></div>
    {documents.length > 0 && <section className="worksheet-documents"><div className="worksheet-detail-section-heading"><div><span className="eyebrow">CSATOLT DOKUMENTUMOK</span><h2>PDF-ek és egyéb fájlok</h2></div></div><div className="worksheet-document-list">{documents.map((file) => <button key={file.id} className="worksheet-document" onClick={() => setPreview(file)}><FileImage size={19}/><span>{file.name}</span><small>Előnézet megnyitása</small></button>)}</div></section>}
    {preview && <AssetPreviewModal id={preview.id} label={preview.name} credentials={credentials} onClose={() => setPreview(null)}/>}
  </>
}

function WorksheetList({ items, query, credentials, onQuery, onNew, onOpen }: { items: Worksheet[]; query: string; credentials: Credentials; onQuery: (s: string) => void; onNew: () => void; onOpen: (id: string) => void }) {
  return <><div className="page-heading"><div><span className="eyebrow">MŰHELY</span><h1>Feladatlapok</h1><p>Válassz egy feladatlapot az összefoglaló és a nyomtatható képek megnyitásához.</p></div><button className="button primary" onClick={onNew}><Plus size={17}/> Új feladatlap</button></div><div className="toolbar"><label className="search-box"><Search size={17}/><input aria-label="Feladatlapok keresése" placeholder="Keresés cím alapján…" value={query} onChange={(e) => onQuery(e.target.value)}/>{query && <button type="button" className="clear-search" onClick={() => onQuery('')}><X size={15}/></button>}</label><span className="result-count">{items.length} feladatlap</span></div>{items.length ? <div className="worksheet-cover-grid">{items.map((item) => <button type="button" className="worksheet-cover-card" key={item.id} onClick={() => onOpen(item.id)}><span className="worksheet-cover-image">{item.preview_asset_id ? <AuthenticatedImage id={item.preview_asset_id} credentials={credentials} alt="" className="worksheet-cover-photo"/> : <Files size={34}/>}</span><strong>{item.title}</strong></button>)}</div> : <div className="empty-state"><div className="empty-icon"><Files size={22}/></div><strong>{query ? 'Nincs ilyen feladatlap' : 'Még nincs feladatlap'}</strong><span>{query ? 'Próbálj másik keresést.' : 'Hozd létre az első feladatlapot.'}</span>{!query && <button className="button secondary" onClick={onNew}><Plus size={16}/> Feladatlap létrehozása</button>}</div>}</>
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
  function updatePage(index: number, key: keyof Page, value: string | number) { setForm((old) => ({ ...old, pages: old.pages?.map((p,i) => i === index ? { ...p, [key]: value } as Page : p) })) }
  function addCutout() { setForm((old) => ({ ...old, pages: [...(old.pages ?? []), blankPage('CUTOUT', old.pages?.length ?? 1)] })) }
  function movePage(index: number, direction: -1 | 1) { setForm((old) => { const next = [...(old.pages ?? [])]; const target = index + direction; if (target < 1 || target >= next.length) return old; [next[index],next[target]] = [next[target],next[index]]; return { ...old, pages: next.map((p,i) => ({...p,sort_order:i})) } }) }
  function removePage(index: number) { setForm((old) => ({ ...old, pages: old.pages?.filter((_,i) => i !== index).map((p,i) => ({...p,sort_order:i})) })) }
  const totalPages = pages.reduce((total,page) => total + (Number(page.page_count) || 1),0)
  const printableImages = pages.filter((page) => page.print_asset_id && page.print_media_type?.startsWith('image/'))
  const selectedImages = printableImages.filter((page) => !excludedPrintIds.includes(page.print_asset_id!))
  function selectAllImages() { setExcludedPrintIds([]) }
  function togglePrintImage(id: string) { setExcludedPrintIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]) }
  return <form className="editor-form" onSubmit={onSave}><div className="page-heading editor-heading"><div><button type="button" className="back-link" onClick={onCancel}><ArrowLeft size={15}/> Vissza a listához</button><span className="eyebrow">{editing ? 'TARTALOM SZERKESZTÉSE' : 'ÚJ TARTALOM'}</span><h1>{editing ? 'Feladatlap szerkesztése' : 'Új feladatlap'}</h1><p>Rögzítsd az alaplapot, majd add hozzá a kivágható oldalakat.</p></div><div className="heading-actions"><button type="button" className="button secondary" onClick={onCancel}>Mégse</button><button className="button primary" disabled={busy}><Check size={16}/>{busy ? 'Mentés…' : 'Mentés piszkozatként'}</button></div></div><div className="editor-layout"><div className="editor-main"><section className="form-card"><div className="form-card-heading"><div className="step-number">01</div><div><h2>Alapadatok</h2><p>Adj nevet és rövid útmutatást a feladatlaphoz.</p></div></div><div className="form-grid"><label className="field-label span-2">Feladatlap címe *<input required value={form.title} onChange={(e) => setForm((old) => ({ ...old,title:e.target.value }))} placeholder="Például: Melyik állat hova bújt?"/></label><label className="field-label span-2">Rövid leírás<textarea rows={2} value={form.summary ?? ''} onChange={(e) => setForm((old) => ({ ...old,summary:e.target.value }))} placeholder="Röviden, mit talál a feladatlapon?"/></label><label className="field-label span-2">Használati útmutató<textarea rows={3} value={form.instructions ?? ''} onChange={(e) => setForm((old) => ({ ...old,instructions:e.target.value }))} placeholder="Hogyan használja a szülő vagy pedagógus?"/></label><label className="field-label">Papírméret<select value={form.paper_size} onChange={(e) => setForm((old) => ({ ...old,paper_size:e.target.value }))}><option>A4</option><option>A3</option><option>Letter</option></select></label><label className="field-label">Tájolás<select value={form.orientation} onChange={(e) => setForm((old) => ({ ...old,orientation:e.target.value }))}><option value="PORTRAIT">Álló</option><option value="LANDSCAPE">Fekvő</option></select></label><label className="field-label">Színmód<select value={form.color_mode} onChange={(e) => setForm((old) => ({ ...old,color_mode:e.target.value }))}><option value="COLOR">Színes</option><option value="GRAYSCALE">Fekete-fehér</option></select></label><label className="field-label">Felhasználás<select value={form.usage_type} onChange={(e) => setForm((old) => ({ ...old,usage_type:e.target.value }))}><option value="DIGITAL">Digitális</option><option value="PHYSICAL">Fizikai</option><option value="BOTH">Mindkettő</option></select></label><label className="field-label span-2">Biztonsági megjegyzés<textarea rows={2} value={form.safety_notes ?? ''} onChange={(e) => setForm((old) => ({ ...old,safety_notes:e.target.value }))} placeholder="Például: a kivágást felnőtt végezze el."/></label></div></section>
    <section className="form-card pages-card"><div className="form-card-heading"><div className="step-number">02</div><div><h2>Nyomtatható oldalak</h2><p>Az alap feladatlaphoz további kivágható lapokat rendelhetsz.</p></div><span className="page-total">{pages.length} lap · {totalPages} oldal</span></div>{pages.map((page,index) => <div className={`page-editor ${page.page_type === 'MAIN' ? 'main-page-editor' : ''}`} key={`${page.page_type}-${index}`}><div className="page-editor-title"><div className="page-type-icon">{page.page_type === 'MAIN' ? <ClipboardList size={17}/> : <Files size={17}/>}</div><div><strong>{page.page_type === 'MAIN' ? 'Alap feladatlap' : `Kivágható lap ${index}`}</strong><span>{page.page_type === 'MAIN' ? 'A feladat fő oldala' : 'A feladatlaphoz tartozó kiegészítő'}</span></div><div className="page-order-actions"><button type="button" className="icon-button" disabled={index <= 1} title="Mozgatás fel" onClick={() => movePage(index,-1)}><ArrowUp size={15}/></button><button type="button" className="icon-button" disabled={index === 0 || index === pages.length-1} title="Mozgatás le" onClick={() => movePage(index,1)}><ArrowDown size={15}/></button>{page.page_type === 'CUTOUT' && <button type="button" className="icon-button remove-button" title="Lap törlése" onClick={() => removePage(index)}><X size={16}/></button>}</div></div><div className="page-form-grid"><label className="field-label">Lap megnevezése<input value={page.title ?? ''} disabled={page.page_type === 'MAIN'} onChange={(e) => updatePage(index,'title',e.target.value)} placeholder="Például: Kivágható állatfigurák"/></label><label className="field-label narrow-field">Oldalszám<input type="number" min="1" value={page.page_count} onChange={(e) => updatePage(index,'page_count',Number(e.target.value))}/></label><label className="field-label narrow-field">Tájolás<select value={page.orientation} onChange={(e) => updatePage(index,'orientation',e.target.value)}><option value="PORTRAIT">Álló</option><option value="LANDSCAPE">Fekvő</option></select></label><label className="field-label">Megjegyzés<input value={page.notes ?? ''} onChange={(e) => updatePage(index,'notes',e.target.value)} placeholder="Opcionális útmutató"/></label></div><div className="upload-row"><UploadBox label="Előnézeti kép" accept="image/png,image/jpeg,image/webp" assetId={page.preview_asset_id} credentials={credentials} assetName={page.preview_name} onUpload={(files) => onUpload(files,index,'preview_asset_id')}/><UploadBox label="Nyomtatási fájl" accept="application/pdf,image/png,image/jpeg,image/webp" assetId={page.print_asset_id} assetName={page.print_name} credentials={credentials} multiple onUpload={(files) => onUpload(files,index,'print_asset_id')}/></div></div>)}{printableImages.length > 0 && <div className="bulk-print-panel"><div className="bulk-print-heading"><div><strong>Képek nyomtatása</strong><span>{selectedImages.length} / {printableImages.length} kép kijelölve</span></div><div><button type="button" className="text-button" onClick={selectAllImages}>Összes kijelölése</button><button type="button" className="text-button muted" onClick={() => setExcludedPrintIds(printableImages.map((page) => page.print_asset_id!))}>Kijelölés törlése</button><button type="button" className="button secondary" disabled={!selectedImages.length} onClick={() => printImageAssets(credentials, selectedImages.map((page) => page.print_asset_id!))}><Printer size={15}/> Nyomtatás</button></div></div><div className="bulk-print-files">{printableImages.map((page) => <label key={page.print_asset_id}><input type="checkbox" checked={!excludedPrintIds.includes(page.print_asset_id!)} onChange={() => togglePrintImage(page.print_asset_id!)}/><span>{page.print_name || page.title || 'Kép'}</span></label>)}</div></div>}<button type="button" className="add-cutout" onClick={addCutout}><Plus size={16}/> Kivágható lap hozzáadása</button></section></div><aside className="editor-aside"><section className="form-card side-form-card"><div className="form-card-heading compact"><div className="step-number">03</div><div><h2>Kategorizálás</h2><p>Segíts, hogy később könnyű legyen megtalálni.</p></div></div>{([['AGE','KOROSZTÁLY'],['TASK_TYPE','FELADATTÍPUS'],['TOPIC','TÉMAKÖR'],['SKILL','KÉSZSÉGTERÜLET']] as [Term['term_type'],string][]).map(([type,label]) => <fieldset className="term-fieldset" key={type}><legend>{label}</legend><div className="term-options">{terms.filter((term) => term.term_type === type).map((term) => { const selected = form.terms?.some((t) => t.id === term.id); return <button type="button" key={term.id} className={`term-chip ${selected?'selected':''}`} onClick={() => setForm((old) => ({ ...old,terms:selected ? old.terms?.filter((t) => t.id !== term.id) : [...(old.terms ?? []),term] }))}>{selected && <Check size={13}/>} {term.name}</button> })}{!terms.some((term) => term.term_type === type) && <span className="muted-small">Még nincs felvéve elem</span>}</div></fieldset>)}<label className="field-label status-label">Tartalom státusza<select value={form.status} onChange={(e) => setForm((old) => ({ ...old,status:e.target.value }))}><option value="DRAFT">Piszkozat</option><option value="REVIEW">Ellenőrzésre vár</option><option value="PUBLISHED">Publikált</option></select></label><div className="publish-note"><ShieldCheck size={15}/><span>A publikált tartalom később megjelenhet a szülői katalógusban.</span></div></section><section className="print-summary"><div className="print-summary-icon"><Files size={17}/></div><div><span>NYOMTATÁSI ÖSSZESÍTŐ</span><strong>{pages.length} elem · {totalPages} oldal</strong><small>{form.paper_size} · {form.orientation === 'LANDSCAPE' ? 'fekvő' : 'álló'} · {form.color_mode === 'GRAYSCALE' ? 'fekete-fehér' : 'színes'}</small></div></section></aside></div></form>
}

function UploadBox({ label, accept, assetId, assetName, credentials, multiple = false, onUpload }: { label: string; accept: string; assetId?: string; assetName?: string; credentials:Credentials; multiple?: boolean; onUpload: (files: File[]) => void }) {
  const [previewOpen, setPreviewOpen] = useState(false)
  return <div className="upload-control"><label className={`upload-box ${assetId ? 'has-file' : ''}`}><input type="file" accept={accept} multiple={multiple} onChange={(e) => { const files=Array.from(e.currentTarget.files ?? []); if(files.length) onUpload(files); e.currentTarget.value='' }}/>{assetId ? <><div className="asset-preview"><AuthenticatedAsset id={assetId} credentials={credentials}/></div><div><strong>{assetName || label}</strong><span>Feltöltve · kattints a cseréhez</span></div></> : <><div className="upload-icon"><Upload size={16}/></div><div><strong>{label}</strong><span>{multiple ? 'Több kép vagy PDF is kijelölhető · max. 20 MB/fájl' : 'PDF vagy kép · max. 20 MB'}</span></div></>}</label>{assetId && <button type="button" className="button secondary preview-file-button" onClick={() => setPreviewOpen(true)}>Előnézet</button>}{previewOpen && assetId && <AssetPreviewModal id={assetId} label={assetName || label} credentials={credentials} onClose={() => setPreviewOpen(false)}/>}</div>
}

function ActivityView({ items, form, setForm, terms, busy, onSave, onEdit }: { items: ActivityItem[]; form: ActivityForm; setForm: Dispatch<SetStateAction<ActivityForm>>; terms:Term[]; busy: boolean; onSave: (e: FormEvent) => void; onEdit:(id:string)=>void }) {
  return <><div className="page-heading"><div><span className="eyebrow">ÖNÁLLÓ TARTALOM</span><h1>Játékötletek</h1><p>Leírásként is megálló játékok, amelyekhez nem kell feladatlap.</p></div></div><div className="activity-layout"><form className="form-card activity-form" onSubmit={onSave}><div className="form-card-heading"><div className="step-number"><Sparkles size={17}/></div><div><h2>{form.id ? 'Játékötlet szerkesztése' : 'Új játékötlet'}</h2><p>Rögzítsd a játék alapadatait.</p></div>{form.id&&<button type="button" className="icon-button" title="Új játékötlet" onClick={()=>setForm({id:'',title:'',summary:'',durationMinutes:'',instructions:'',materials:'',preparation:'',easyVariant:'',challengeVariant:'',safetyNotes:'',locationType:'ANY',status:'DRAFT',termIds:[]})}><Plus size={16}/></button>}</div><label className="field-label">Játék neve *<input required value={form.title} onChange={(e)=>setForm((old)=>({...old,title:e.target.value}))} placeholder="Például: Színes kupakvadászat"/></label><label className="field-label">Rövid leírás<textarea rows={3} value={form.summary} onChange={(e)=>setForm((old)=>({...old,summary:e.target.value}))} placeholder="Miről szól a játék?"/></label><label className="field-label">Játék menete<textarea rows={5} value={form.instructions} onChange={(e)=>setForm((old)=>({...old,instructions:e.target.value}))} placeholder="Írd le lépésről lépésre…"/></label><label className="field-label">Szükséges eszközök<textarea rows={2} value={form.materials} onChange={(e)=>setForm((old)=>({...old,materials:e.target.value}))} placeholder="Mire lesz szükségetek?"/></label><label className="field-label">Előkészület<textarea rows={2} value={form.preparation} onChange={(e)=>setForm((old)=>({...old,preparation:e.target.value}))} placeholder="Mit készíts elő a játék előtt?"/></label><div className="two-fields"><label className="field-label">Könnyített változat<textarea rows={3} value={form.easyVariant} onChange={(e)=>setForm((old)=>({...old,easyVariant:e.target.value}))}/></label><label className="field-label">Nehezített változat<textarea rows={3} value={form.challengeVariant} onChange={(e)=>setForm((old)=>({...old,challengeVariant:e.target.value}))}/></label></div><label className="field-label">Biztonsági tudnivalók<textarea rows={2} value={form.safetyNotes} onChange={(e)=>setForm((old)=>({...old,safetyNotes:e.target.value}))} placeholder="Felnőtt felügyelete, apró elemek stb."/></label><label className="field-label">Helyszín<select value={form.locationType} onChange={(e)=>setForm((old)=>({...old,locationType:e.target.value}))}><option value="ANY">Benti vagy kinti</option><option value="INDOOR">Benti</option><option value="OUTDOOR">Kinti</option></select></label><label className="field-label">Korosztályok, témakörök és készségterületek · több is választható<select multiple size={5} value={form.termIds} onChange={(e)=>setForm((old)=>({...old,termIds:Array.from(e.target.selectedOptions).map((option)=>option.value)}))}>{terms.filter((term)=>term.term_type!=='TASK_TYPE').map((term)=><option key={term.id} value={term.id}>{term.term_type==='AGE'?'Korosztály':term.term_type==='TOPIC'?'Témakör':'Készségterület'} · {term.name}</option>)}</select></label><div className="two-fields"><label className="field-label">Időtartam (perc)<input type="number" min="1" value={form.durationMinutes} onChange={(e)=>setForm((old)=>({...old,durationMinutes:e.target.value}))} placeholder="10"/></label><label className="field-label">Státusz<select value={form.status} onChange={(e)=>setForm((old)=>({...old,status:e.target.value}))}><option value="DRAFT">Piszkozat</option><option value="REVIEW">Ellenőrzés</option><option value="PUBLISHED">Publikált</option></select></label></div><button className="button primary full-width" disabled={busy}><Check size={16}/>{busy?'Mentés…':'Játékötlet mentése'}</button></form><div className="activity-side"><div className="section-title-row"><div><span className="eyebrow">GYŰJTEMÉNY</span><h2>Rögzített ötletek</h2></div><span className="count-badge">{items.length}</span></div>{items.length ? items.map((item)=><button className="activity-item activity-item-button" key={item.id} onClick={()=>onEdit(item.id)}><div className="activity-item-icon"><Sparkles size={17}/></div><div><strong>{item.title}</strong><span>{item.duration_minutes ? `${item.duration_minutes} perc · ` : ''}{item.summary || 'Nincs rövid leírás'}</span></div><StatusPill status={item.status}/></button>) : <div className="empty-state small"><div className="empty-icon"><BookOpen size={20}/></div><strong>Még nincs játékötlet</strong><span>Az első leírás lehet teljesen önálló tartalom.</span></div>}</div></div></>
}

function TaxonomyView({ terms, credentials, reload, notify }: { terms: Term[]; credentials: Credentials; reload: () => Promise<void>; notify: (s:string)=>void }) {
  const [name,setName]=useState(''); const [type,setType]=useState<Term['term_type']>('TOPIC'); const [busy,setBusy]=useState(false)
  async function create(event:FormEvent){event.preventDefault();setBusy(true);try{await api(credentials,'/taxonomy',{method:'POST',body:JSON.stringify({name,termType:type})});setName('');await reload();notify('Kategória hozzáadva')}catch(e){notify((e as Error).message)}finally{setBusy(false)}}
  const groups:[Term['term_type'],string][]=[['AGE','Korosztályok'],['TASK_TYPE','Feladattípusok'],['TOPIC','Témakörök'],['SKILL','Készségterületek']]
  return <><div className="page-heading"><div><span className="eyebrow">KÖZÖS KATALÓGUS</span><h1>Kategóriák</h1><p>Egységes címkék, amelyeket a feladatlapokhoz és játékötletekhez rendelhetsz.</p></div></div><div className="taxonomy-layout"><form className="form-card taxonomy-form" onSubmit={create}><div className="form-card-heading"><div className="step-number"><Plus size={17}/></div><div><h2>Új kategória</h2><p>Adj hozzá egy új választható címkét.</p></div></div><label className="field-label">Kategória típusa<select value={type} onChange={(e)=>setType(e.target.value as Term['term_type'])}><option value="AGE">Korosztály</option><option value="TASK_TYPE">Feladattípus</option><option value="TOPIC">Témakör</option><option value="SKILL">Készségterület</option></select></label><label className="field-label">Megnevezés<input required value={name} onChange={(e)=>setName(e.target.value)} placeholder={type==='AGE'?'Például: 2–3 év':type==='TASK_TYPE'?'Például: Árnyékpárosító':type==='TOPIC'?'Például: Állatok':'Például: Finommotorika'}/></label><button className="button primary" disabled={busy}><Plus size={16}/> Hozzáadás</button></form><div className="taxonomy-groups">{groups.map(([group,title])=><section className="form-card taxonomy-group" key={group}><div className="section-title-row"><div><span className="eyebrow">{group}</span><h2>{title}</h2></div><span className="count-badge">{terms.filter((t)=>t.term_type===group).length}</span></div><div className="taxonomy-chips">{terms.filter((t)=>t.term_type===group).map((term)=><span className="taxonomy-chip" key={term.id}>{term.name}</span>)}{!terms.some((t)=>t.term_type===group)&&<span className="muted-small">Még nincs kategória. Hozz létre egyet.</span>}</div></section>)}</div></div></>
}

function BundleView({ bundles, worksheets, form, setForm, busy, onSave, onEdit }: { bundles:BundleItem[]; worksheets:Worksheet[]; form:BundleForm; setForm:Dispatch<SetStateAction<BundleForm>>; busy:boolean; onSave:(e:FormEvent)=>void; onEdit:(id:string)=>void }) {
  return <><div className="page-heading"><div><span className="eyebrow">KÉSŐBBI ÉRTÉKESÍTÉSHEZ ELŐKÉSZÍTVE</span><h1>Csomagtervek</h1><p>Állíts össze több feladatlapból digitális vagy fizikai ajánlatot.</p></div></div><div className="bundle-layout"><form className="form-card bundle-form" onSubmit={onSave}><div className="form-card-heading"><div className="step-number"><Package size={17}/></div><div><h2>{form.id?'Csomag szerkesztése':'Új csomagterv'}</h2><p>A kivágható oldalak a saját feladatlapjukkal együtt kerülnek a csomagba.</p></div>{form.id&&<button type="button" className="icon-button" title="Új csomag" onClick={()=>setForm({id:'',title:'',slug:'',description:'',bundleType:'DIGITAL',status:'DRAFT',worksheetIds:[]})}><Plus size={16}/></button>}</div><label className="field-label">Csomag neve *<input required value={form.title} onChange={(e)=>setForm((old)=>({...old,title:e.target.value}))} placeholder="Például: Tavaszi állatos kalandok"/></label><label className="field-label">Rövid leírás<textarea rows={3} value={form.description} onChange={(e)=>setForm((old)=>({...old,description:e.target.value}))} placeholder="Kiknek és milyen témában készült?"/></label><div className="two-fields"><label className="field-label">Ajánlat formája<select value={form.bundleType} onChange={(e)=>setForm((old)=>({...old,bundleType:e.target.value}))}><option value="DIGITAL">Digitális letöltés</option><option value="PHYSICAL">Laminált fizikai csomag</option><option value="BOTH">Digitális és fizikai</option></select></label><label className="field-label">Státusz<select value={form.status} onChange={(e)=>setForm((old)=>({...old,status:e.target.value}))}><option value="DRAFT">Piszkozat</option><option value="REVIEW">Ellenőrzés</option><option value="PUBLISHED">Publikált</option></select></label></div><fieldset className="bundle-worksheets"><legend>FELADATLAPOK A CSOMAGBAN · {form.worksheetIds.length}</legend>{worksheets.filter((w)=>w.status!=='ARCHIVED').length?worksheets.filter((w)=>w.status!=='ARCHIVED').map((worksheet)=><label className="bundle-choice" key={worksheet.id}><input type="checkbox" checked={form.worksheetIds.includes(worksheet.id)} onChange={(e)=>setForm((old)=>({...old,worksheetIds:e.target.checked?[...old.worksheetIds,worksheet.id]:old.worksheetIds.filter((id)=>id!==worksheet.id)}))}/><span className="bundle-check"><Check size={12}/></span><span className="bundle-choice-name"><strong>{worksheet.title}</strong><small>{worksheet.total_pages??worksheet.totalPages??1} oldal · {worksheet.paper_size??'A4'}</small></span><StatusPill status={worksheet.status}/></label>):<span className="muted-small">Előbb hozz létre feladatlapot.</span>}</fieldset><button className="button primary full-width" disabled={busy}><Check size={16}/>{busy?'Mentés…':'Csomagterv mentése'}</button></form><div className="bundle-list"><div className="section-title-row"><div><span className="eyebrow">ÖSSZEÁLLÍTÁSOK</span><h2>Mentett csomagtervek</h2></div><span className="count-badge">{bundles.length}</span></div>{bundles.length?bundles.map((bundle)=><button className="bundle-card" key={bundle.id} onClick={()=>onEdit(bundle.id)}><div className="bundle-card-icon"><Package size={19}/></div><span className="bundle-card-main"><strong>{bundle.title}</strong><small>{bundle.worksheet_count??0} feladatlap · {bundle.bundle_type==='PHYSICAL'?'Fizikai':bundle.bundle_type==='BOTH'?'Digitális + fizikai':'Digitális'}</small></span><StatusPill status={bundle.status}/></button>):<div className="empty-state small"><div className="empty-icon"><Package size={20}/></div><strong>Még nincs csomagterv</strong><span>Válassz ki több feladatlapot a csomag összeállításához.</span></div>}<div className="bundle-note"><ShieldCheck size={16}/><span>Ez egy tartalmi csomagterv. Rendelés, árazás, készlet és fizetés későbbi fejlesztési ütem.</span></div></div></div></>
}

function ComingSoon({ title, eyebrow, copy, icon }: { title:string; eyebrow:string; copy:string; icon:ReactNode }) { return <><div className="page-heading"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>Az ajánlatok előkészítése egy helyen.</p></div></div><div className="coming-card"><div className="coming-icon">{icon}</div><span className="eyebrow">ELŐKÉSZÍTÉS ALATT</span><h2>Hamarosan összeállíthatod<br/>a saját csomagjaidat.</h2><p>{copy}</p><div className="coming-steps"><span><i>01</i> Válaszd ki a feladatlapokat</span><span><i>02</i> Add meg a digitális vagy fizikai formát</span><span><i>03</i> Készíts belőle ajánlatot</span></div></div></> }
