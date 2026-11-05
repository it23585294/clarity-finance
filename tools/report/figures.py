"""Editable, ASCII-labelled report diagrams. No application or device access."""
from pathlib import Path
from html import escape
import math

TEAL = '#237D68'
MINT = '#EEF9F3'
INK = '#182B29'
MUTED = '#506560'

def text(parts, x, y, value, size=22, bold=False, fill=INK, anchor='start'):
    value.encode('ascii', 'strict')
    parts.append(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{"bold" if bold else "normal"}" fill="{fill}" text-anchor="{anchor}">{escape(value)}</text>')

def start(title, width=1000, height=560):
    return [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{escape(title)}"><rect width="100%" height="100%" fill="white"/><g font-family="Helvetica">']

def rect(parts, x, y, width, height, fill=MINT, radius=10):
    parts.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="{radius}" fill="{fill}" stroke="{TEAL}" stroke-width="2"/>')

def box(parts, x, y, width, height, title, lines=()):
    rect(parts, x, y, width, height)
    text(parts, x+16, y+30, title, 25, True)
    for i, value in enumerate(lines):
        text(parts, x+16, y+59+i*27, value)

def arrow(parts, x1, y1, x2, y2):
    parts.append(f'<path d="M{x1},{y1} L{x2},{y2}" stroke="{MUTED}" stroke-width="2.5" fill="none"/>')
    a=math.atan2(y2-y1,x2-x1);bx=x2-11*math.cos(a);by=y2-11*math.sin(a)
    parts.append(f'<polygon points="{x2},{y2} {bx+5*math.sin(a)},{by-5*math.cos(a)} {bx-5*math.sin(a)},{by+5*math.cos(a)}" fill="{MUTED}"/>')

def save(folder, name, parts):
    content=''.join(parts)+'</g></svg>'
    content.encode('ascii','strict')
    (folder/(name+'.svg')).write_text(content,encoding='utf-8',newline='\n')

def phone(index):
    p=[]
    rect(p,5,5,295,515,'#F4F7F6',14)
    if index==0:
        text(p,21,43,'Overview',28,True)
        text(p,21,80,'November 2026',23)
        rect(p,20,98,265,44,TEAL);text(p,152,128,'Add Expense',24,True,'white','middle')
        for y,label,value in [(180,'Received income','LKR 120,000.00'),(263,'Recorded expenses','LKR 45,000.00'),(346,'Recorded surplus','LKR 75,000.00')]:
            text(p,21,y,label,23);text(p,21,y+34,value,27,True,TEAL)
        text(p,21,431,'Other summaries',23,True)
        text(p,21,461,'Crypto / pending / goal',22)
        text(p,21,500,'Overview   History   Plans',20)
    elif index==1:
        text(p,21,43,'Add Expense',28,True)
        text(p,21,81,'Amount (LKR)',23)
        rect(p,20,95,265,49,'white');text(p,32,129,'400.00',28,True)
        for y,label,value in [(175,'Category','Food & Dining'),(247,'Spending type','Committed'),(319,'Payment channel','Cash'),(391,'Actual date','5 Nov 2026')]:
            text(p,21,y,label,22);text(p,21,y+28,value,24,True)
        rect(p,20,453,265,46,TEAL);text(p,152,485,'Save expense',24,True,'white','middle')
    else:
        text(p,21,43,'History',28,True)
        text(p,21,81,'November 2026',23)
        text(p,21,122,'Expenses | Income',23,True)
        text(p,21,166,'All categories',23)
        text(p,21,211,'Month: LKR 45,000.00',24,True,TEAL)
        rect(p,20,235,265,106,'white')
        text(p,32,268,'Transport',23,True);text(p,32,297,'LKR 750.00',26,True)
        text(p,32,326,'5 Nov 2026',22)
        rect(p,20,354,265,106,'white')
        text(p,32,387,'Food & Dining',23,True);text(p,32,416,'LKR 500.00',26,True)
        text(p,32,445,'5 Nov 2026',22)
        text(p,21,500,'Inspect / edit expense',22)
    return ''.join(p)

def generate(folder):
    folder=Path(folder);folder.mkdir(parents=True,exist_ok=True)
    p=start('Owner use cases',1000,430)
    rect(p,210,5,780,420,'white')
    text(p,230,35,'Clarity Finance',24,True)
    p.append(f'<circle cx="80" cy="170" r="19" fill="none" stroke="{INK}" stroke-width="3"/><path d="M80,189 L80,245 M45,210 L115,210 M80,245 L48,280 M80,245 L112,280" fill="none" stroke="{INK}" stroke-width="3"/>')
    text(p,80,315,'App user',22,True,INK,'middle');text(p,80,343,'(owner)',22,False,INK,'middle')
    nodes=[(410,110,'UC01 Identity','Register / sign in / out'),(800,110,'UC02-03 Cash records','Expenses / received income'),
           (410,235,'UC04-05 Results','Projects / receipts / crypto'),(800,235,'UC06 Review','Monthly history / insights'),
           (410,360,'UC07-08 Plans','Goal / recurring review'),(800,360,'FR21 Catalogue','Add / rename / archive')]
    for x,y,_,_ in nodes:p.append(f'<path d="M115,210 L{x-171},{y}" stroke="{MUTED}" stroke-width="2" fill="none"/>')
    for x,y,title,subtitle in nodes:
        p.append(f'<ellipse cx="{x}" cy="{y}" rx="175" ry="47" fill="{MINT}" stroke="{TEAL}" stroke-width="2"/>')
        text(p,x,y-3,title,23,True,INK,'middle');text(p,x,y+27,subtitle,22,False,INK,'middle')
    save(folder,'use-cases',p)

    p=start('Three annotated wireframes',1000,855)
    titles=['W1 Overview','W2 Add Expense','W3 History']
    notes=[['1  Capture is the first action.','2  Cash measures stay grouped.','3  Other results stay separate.'],
           ['1  Amount comes first.','2  Classifications are explicit.','3  Date can be backdated.'],
           ['1  Month and filters stay visible.','2  Inspect underlying records.','3  Back restores list context.']]
    for i,x in enumerate([15,350,685]):
        text(p,x,35,titles[i],27,True)
        p.append(f'<g id="wireframe-{i+1}" transform="translate({x},60)">{phone(i)}</g>')
        for j,note in enumerate(notes[i]):
            # Break annotations into readable lines rather than shrinking text.
            words=note.split();lines=[];line=''
            for word in words:
                if len(line)+len(word)+1>27:lines.append(line);line=word
                else:line=(line+' '+word).strip()
            if line:lines.append(line)
            for k,value in enumerate(lines):text(p,x+3,620+j*69+k*26,value,22)
        single=start(titles[i],320,790)
        single.append('<g transform="translate(8,10)">'+phone(i)+'</g>')
        for j,note in enumerate(notes[i]):
            words=note.split();line='';wrapped=[]
            for word in words:
                if len(line)+len(word)+1>27:wrapped.append(line);line=word
                else:line=(line+' '+word).strip()
            wrapped.append(line)
            for k,value in enumerate(wrapped):text(single,12,575+j*69+k*26,value,22)
        save(folder,'wireframe-'+str(i+1),single)
    text(p,18,830,'Fictional static mockups; not live records or fixed-height implementation layouts.',22,False,MUTED)
    save(folder,'wireframes',p)

    p=start('MVVM and dependency ownership',1000,540)
    box(p,15,15,620,90,'Compose + Navigation',['Render state; forward user actions'])
    box(p,15,155,620,90,'Koin ViewModels / StateFlow',['ViewModelStore ownership; viewModelScope'])
    box(p,15,295,620,90,'Repositories + exact domain helpers',['Data access / calculations / reconciliation'])
    box(p,15,435,295,90,'Firebase adapters',['Auth / Firestore'])
    box(p,340,435,295,90,'DataStore',['Immutable proposals'])
    box(p,690,15,295,145,'Process singletons',['Clients / stores','Retained Tasks','Repositories'])
    box(p,690,255,295,187,'Session coordinator',['Application startup','UID-scoped workers','Separate supervisor','No force-stop promise'])
    arrow(p,325,105,325,155);arrow(p,325,245,325,295);arrow(p,220,385,160,435);arrow(p,450,385,485,435)
    arrow(p,835,160,835,255);arrow(p,690,348,635,348)
    save(folder,'architecture',p)

    p=start('Owner-scoped Firestore schema',1000,530)
    box(p,300,10,400,75,'users/{uid}',[''])
    for x in [250,750]:arrow(p,500,85,x,135)
    box(p,15,135,470,145,'Cash and separate results',['expenses / incomeReceipts','cryptoResults','expenseCategories / incomeSources'])
    box(p,515,135,470,145,'Agreements',['expectedPayments/{id}','  milestones/{id}','Linked income receipts update counters'])
    box(p,15,325,470,175,'One goal and history',['savingsGoals/active','  entries/{id}','  operations/{id}','  rejections/{id}'])
    box(p,515,325,470,175,'Recurring and mutation proofs',['recurringTemplates / recurringOccurrences','recurringOperations','syncOperations / syncFences','syncStates: version / retired IDs'])
    # The lower groups are siblings below users/{uid}, not children of cash/projects.
    p.append(f'<path d="M330,85 L5,105 L5,310 L250,310" stroke="{MUTED}" stroke-width="2.5" fill="none"/>')
    arrow(p,250,310,250,325)
    p.append(f'<path d="M670,85 L995,105 L995,310 L750,310" stroke="{MUTED}" stroke-width="2.5" fill="none"/>')
    arrow(p,750,310,750,325)
    save(folder,'schema',p)

    p=start('Durable operation protocol',1000,545)
    box(p,15,10,970,75,'1  Persist immutable proposal',['UID + type + record + stable operation ID'])
    box(p,15,125,970,75,'2  Reconcile accepted proof first',['Matching proof acknowledges exactly once before mutable-state checks'])
    box(p,15,245,470,115,'3a  Matching proof: acknowledge',['Local cleanup is separate bookkeeping','Cleanup failure cannot repeat money'])
    box(p,515,245,470,115,'3b  No proof: original atomic attempt',['Expected version; immutable payload','Business mutation and proof together'])
    box(p,15,410,470,115,'4a  Uncertain: retain and reconcile',['Timeout / access error is not rejection','An earlier Task may still commit'])
    box(p,515,410,470,115,'4b  Resolve before replacement',['Receipt wins, or authoritative fence','prevents old commit before correction'])
    arrow(p,500,85,500,125);arrow(p,350,200,250,245);arrow(p,650,200,750,245)
    arrow(p,650,360,250,410);arrow(p,485,465,515,465)
    save(folder,'protocol',p)
