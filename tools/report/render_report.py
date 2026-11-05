"""Build the canonical documentation only; never launches Android/Firebase.

Requires existing python-docx, Markdown, lxml and PyMuPDF document tools.
--python-tools may point to an existing private local tool installation.
PDF export is explicit and requires Microsoft Word/cscript on Windows.
"""
from pathlib import Path
import argparse,sys,re,json,subprocess,zipfile,hashlib,datetime,shutil

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--python-tools',type=Path)
    parser.add_argument('--export-pdf',action='store_true')
    args=parser.parse_args()
    sys.dont_write_bytecode=True
    if args.python_tools:sys.path.insert(0,str(args.python_tools.resolve()))
    import markdown,fitz
    from lxml import html as LH,etree
    from docx import Document
    from docx.shared import Inches,Pt,RGBColor
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.enum.style import WD_STYLE_TYPE
    from figures import generate
    root=Path(__file__).resolve().parents[2]
    folder=root/'docs/report';source=folder/'Clarity_Finance_Report.md'
    content=source.read_text(encoding='utf-8',errors='strict')
    content.encode('ascii','strict')
    chunks=re.split(r'<!-- page: ([^>]+) -->',content)
    pages=[(chunks[i].strip(),chunks[i+1].strip()) for i in range(1,len(chunks),2)]
    assert len(pages)==17,'Unexpected source page plan'
    generate(folder/'figures')
    for svg in sorted((folder/'figures').glob('*.svg')):
        data=svg.read_text(encoding='utf-8',errors='strict');data.encode('ascii','strict')
        with fitz.open(stream=data.encode('utf-8'),filetype='svg') as graphic:
            graphic[0].get_pixmap(matrix=fitz.Matrix(1.8,1.8)).save(svg.with_suffix('.png'))

    doc=Document();sec=doc.sections[0]
    sec.page_width=Inches(8.2677);sec.page_height=Inches(11.6929)
    sec.top_margin=sec.bottom_margin=Inches(.73);sec.left_margin=sec.right_margin=Inches(.77)
    sec.header_distance=sec.footer_distance=Inches(.3)
    styles=doc.styles
    normal=styles['Normal'];normal.font.name='Calibri';normal.font.size=Pt(11)
    normal.paragraph_format.line_spacing=1.05;normal.paragraph_format.space_after=Pt(6)
    for name,size in [('Title',25),('Subtitle',15),('Heading 1',18),('Heading 2',12)]:
        styles[name].font.name='Calibri';styles[name].font.size=Pt(size);styles[name].font.color.rgb=RGBColor.from_string('237D68')
        styles[name].paragraph_format.space_before=Pt(0);styles[name].paragraph_format.space_after=Pt(7)
        styles[name].paragraph_format.keep_with_next=True
    if 'TOC 1' not in styles:styles.add_style('TOC 1',WD_STYLE_TYPE.PARAGRAPH)
    styles['TOC 1'].font.name='Calibri';styles['TOC 1'].font.size=Pt(10.5)
    styles['TOC 1'].paragraph_format.space_after=Pt(4)
    styles['Caption'].font.name='Calibri';styles['Caption'].font.size=Pt(9.5)
    styles['Caption'].paragraph_format.space_after=Pt(6)
    styles['Caption'].paragraph_format.keep_with_next=False
    header=sec.header.paragraphs[0];header.text='CLARITY FINANCE | SE3092';header.runs[0].font.size=Pt(9)
    footer=sec.footer.paragraphs[0];footer.alignment=WD_ALIGN_PARAGRAPH.RIGHT
    field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'PAGE');footer._p.append(field)

    def inline(p,node,bold=False,code=False):
        def add(t):
            if t:
                r=p.add_run(t);r.bold=bold
                if code:r.font.name='Consolas';r.font.size=Pt(9.5)
        add(node.text)
        for c in node:
            if c.tag=='br':p.add_run().add_break()
            else:inline(p,c,bold or c.tag=='strong',code or c.tag=='code')
            add(c.tail)

    def image(p,im,maxw=6.65,maxh=6.7):
        path=(folder/im.get('src')).resolve()
        assert path.is_relative_to(folder.resolve()) and path.is_file()
        with fitz.open(path) as f:w,h=f[0].rect.width,f[0].rect.height
        factor=min(maxw/w,maxh/h)
        p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.keep_with_next=True
        shape=p.add_run().add_picture(str(path),width=Inches(w*factor),height=Inches(h*factor))
        shape._inline.docPr.set('descr',im.get('alt',''))

    for page_index,(label,body) in enumerate(pages):
        if page_index:doc.add_page_break()
        tree=LH.fromstring('<main>'+markdown.markdown(body,extensions=['tables','fenced_code'])+'</main>')
        elems=list(tree);skip=set();skip_toc=False
        for i,el in enumerate(elems):
            if i in skip:continue
            tag=el.tag
            if tag in ['h1','h2','h3']:
                title=el.text_content()
                if title=='Contents':
                    doc.add_heading(title,2)
                    p=doc.add_paragraph();f=OxmlElement('w:fldSimple');f.set(qn('w:instr'),'TOC \\o "1-1" \\h \\z \\u');p._p.append(f);skip_toc=True
                elif tag=='h1':doc.add_heading(title,0)
                elif page_index==0 and tag=='h2':doc.add_paragraph(title,'Subtitle')
                else:doc.add_heading(title,1 if tag=='h2' else 2)
            elif tag=='p':
                imgs=el.findall('img')
                if imgs:
                    following=elems[i+1].findall('img') if i+1<len(elems) and elems[i+1].tag=='p' else []
                    if imgs[0].get('src','').startswith('screenshots/') and following:
                        table=doc.add_table(rows=1,cols=2);table.alignment=WD_TABLE_ALIGNMENT.CENTER
                        table.rows[0]._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
                        for cell,im in zip(table.rows[0].cells,imgs+following):
                            image(cell.paragraphs[0],im,3.13,5.6);cell.add_paragraph(im.get('alt',''),'Caption')
                        skip.add(i+1)
                    else:
                        p=doc.add_paragraph();maxh=5.65 if 'wireframes' in imgs[0].get('src','') else 3.8
                        if 'use-cases' in imgs[0].get('src',''):maxh=2.55
                        image(p,imgs[0],6.65,maxh);doc.add_paragraph(imgs[0].get('alt',''),'Caption')
                else:p=doc.add_paragraph();inline(p,el)
            elif tag=='table':
                rows=el.findall('.//tr');cols=max(len(r) for r in rows)
                table=doc.add_table(rows=0,cols=cols);table.alignment=WD_TABLE_ALIGNMENT.CENTER
                table.autofit=False
                ratios=[.35,.65] if cols==2 else [1/cols]*cols
                for col,ratio in zip(table.columns,ratios):col.width=Inches(6.68*ratio)
                borders=OxmlElement('w:tblBorders')
                for edge in ['top','bottom','insideH']:
                    b=OxmlElement('w:'+edge);b.set(qn('w:val'),'single');b.set(qn('w:sz'),'4');b.set(qn('w:color'),'237D68' if edge!='insideH' else 'D8E7DF');borders.append(b)
                table._tbl.tblPr.append(borders)
                for n,row in enumerate(rows):
                    cells=table.add_row().cells;pr=table.rows[-1]._tr.get_or_add_trPr();pr.append(OxmlElement('w:cantSplit'))
                    if n==0:pr.append(OxmlElement('w:tblHeader'))
                    for cell,child,ratio in zip(cells,row,ratios):
                        cell.width=Inches(6.68*ratio)
                        p=cell.paragraphs[0];inline(p,child)
                        p.paragraph_format.line_spacing=1.0;p.paragraph_format.space_before=Pt(3);p.paragraph_format.space_after=Pt(3)
                        if n==0:
                            sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'EEF9F3');cell._tc.get_or_add_tcPr().append(sh)
                        for r in p.runs:r.font.size=Pt(10.5);r.font.color.rgb=RGBColor.from_string('182B29');r.bold=n==0
                spacer=doc.add_paragraph();spacer.paragraph_format.space_after=Pt(1);spacer.paragraph_format.space_before=Pt(0);spacer.paragraph_format.line_spacing=Pt(2)
            elif tag in ['ul','ol']:
                if skip_toc:skip_toc=False;continue
                for li in el.findall('li'):p=doc.add_paragraph(style='List Bullet');inline(p,li)
            elif tag=='pre':
                p=doc.add_paragraph();p.add_run(el.text_content()).font.name='Consolas'
    props=doc.core_properties
    props.title='Clarity Finance - Technical Report';props.subject='SE3092 Assignment 01'
    props.author='Confirmed project team; AI-assisted report - see disclosure'
    props.last_modified_by='AI-assisted documentation preparation'
    props.created=props.modified=datetime.datetime.now(datetime.timezone.utc)
    target=folder/'Clarity_Finance_Report.docx';doc.save(target)
    if args.export_pdf:
        if sys.platform!='win32':raise SystemExit('PDF export requires Word on Windows; DOCX and figures are ready.')
        subprocess.run(['cscript','//nologo',str(Path(__file__).with_name('export_pdf.vbs')),str(target),str(target.with_suffix('.pdf'))],check=True)
        # Strip Office-profile identity while preserving every non-metadata part.
        with zipfile.ZipFile(target) as z:
            entries={n:z.read(n) for n in z.namelist()}
        core=etree.fromstring(entries['docProps/core.xml']);ns={'cp':'http://schemas.openxmlformats.org/package/2006/metadata/core-properties'}
        core.find('cp:lastModifiedBy',ns).text='AI-assisted documentation preparation; see disclosure'
        temporary=target.with_suffix('.temporary.docx')
        with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as z:
            for name,data in entries.items():z.writestr(name,etree.tostring(core,xml_declaration=True,encoding='UTF-8',standalone=True) if name=='docProps/core.xml' else data)
        temporary.replace(target)
    print(json.dumps({'source_sections':len(pages),'source_words':len(content.split()),'docx':target.relative_to(root).as_posix(),'pdf_exported':args.export_pdf}))

if __name__=='__main__':main()
