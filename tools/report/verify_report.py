"""Validate only the canonical report; render inspection images outside the repo."""
from pathlib import Path
import argparse,sys,json,re,hashlib,zipfile

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--python-tools',type=Path)
    parser.add_argument('--inspection-output',type=Path,required=True)
    args=parser.parse_args();sys.dont_write_bytecode=True
    if args.python_tools:sys.path.insert(0,str(args.python_tools.resolve()))
    import fitz,markdown
    from lxml import etree,html as LH
    root=Path(__file__).resolve().parents[2];folder=root/'docs/report'
    output=args.inspection_output.resolve()
    if output.is_relative_to(root):raise SystemExit('Inspection output must stay outside the repository.')
    output.mkdir(parents=True,exist_ok=True)
    source=(folder/'Clarity_Finance_Report.md').read_text(encoding='utf-8',errors='strict');source.encode('ascii','strict')
    assert not re.search(r'^#{1,3}\s+References\s*$',source,re.MULTILINE)
    assert not re.search(r'\[\d+(?:[,;]\s*\d+)*\]',source),'Dangling numbered citation'
    tree=LH.fromstring('<main>'+markdown.markdown(source,extensions=['tables'])+'</main>')
    images=tree.findall('.//img')
    for im in images:assert (folder/im.get('src')).is_file(),im.get('src')
    figures=[]
    for p in sorted((folder/'figures').glob('*.svg')):
        text=p.read_text(encoding='utf-8',errors='strict');text.encode('ascii','strict')
        svg=etree.fromstring(text.encode('utf-8'))
        width=float(svg.get('width'));height=float(svg.get('height'))
        for t in svg.findall('.//{http://www.w3.org/2000/svg}text'):
            label=''.join(t.itertext());size=float(t.get('font-size'))
            assert size>=20,(p.name,label,'small figure label')
            x=float(t.get('x'));y=float(t.get('y'))
            for parent in t.iterancestors():
                tr=parent.get('transform','');match=re.search(r'translate\(([-.\d]+)[, ]+([-.\d]+)\)',tr)
                if match:x+=float(match[1]);y+=float(match[2])
            f=fitz.Font('hebo' if t.get('font-weight')=='bold' else 'helv');length=f.text_length(label,fontsize=size)
            if t.get('text-anchor')=='middle':x-=length/2
            assert x>=-1 and x+length<=width+1 and 0<y<height,(p.name,label,'outside figure canvas')
        assert p.with_suffix('.png').is_file()
        figures.append(p.name)

    namespace={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
    with zipfile.ZipFile(folder/'Clarity_Finance_Report.docx') as z:
        xml=etree.fromstring(z.read('word/document.xml'))
        core=etree.fromstring(z.read('docProps/core.xml'))
        last=core.find('{http://schemas.openxmlformats.org/package/2006/metadata/core-properties}lastModifiedBy').text
        assert last=='AI-assisted documentation preparation; see disclosure'
        paragraphs=[]
        for p in xml.xpath('//w:p',namespaces=namespace):
            paragraphs.append(''.join(n.text or '' if n.tag.endswith('}t') else '\n' if n.tag.endswith('}br') else '' for n in p.iter()))
    normalize=lambda t:re.sub(r'\s+',' ',t).strip()
    doc_text=normalize('\n'.join(paragraphs))
    for el in tree:
        if el.tag=='p' and not el.findall('img'):
            t=normalize(el.text_content())
            if t:assert t in doc_text,t[:80]
        if el.tag=='table':
            for cell in el.findall('.//td')+el.findall('.//th'):
                assert normalize(cell.text_content()) in doc_text
    pdf=fitz.open(folder/'Clarity_Finance_Report.pdf')
    assert len(pdf)==17,('Report overflow/underflow',len(pdf))
    headings=re.findall(r'^## (\d+\. .+)$',source,re.MULTILINE)
    assert len(headings)==15
    page_inventory=[];bounds=[]
    for i,page in enumerate(pdf):
        content=page.get_text()
        assert '\ufffd' not in content
        if i>=2:assert headings[i-2] in content,('Unexpected chapter page',i+1)
        for block in page.get_text('dict')['blocks']:
            if block['type']!=0:continue
            for line in block['lines']:
                for span in line['spans']:
                    x0,y0,x1,y1=span['bbox']
                    if x0<12 or x1>page.rect.width-12 or y0<8 or y1>page.rect.height-8:bounds.append([i+1,span['text']])
        assert len(content.split())>=100,('Unexpected near-blank page',i+1)
        page.get_pixmap(matrix=fitz.Matrix(1.2,1.2)).save(output/f'page-{i+1:02d}.png')
        page_inventory.append({'page':i+1,'words':len(content.split()),'chapter':headings[i-2] if i>=2 else 'Cover' if i==0 else 'Summary/contents'})
    assert not bounds,bounds
    assert 'UC02-03' in (folder/'figures/use-cases.svg').read_text(encoding='utf-8')
    for p in sorted(folder.rglob('*')):
        if p.is_file() and p.suffix in ['.md','.svg','.json']:
            data=p.read_text(encoding='utf-8',errors='strict')
            assert not any(s in data for s in ['\ufffd','\u00e2\u20ac','\u00c3\u00a2','\u00c2\u00a3']),p.name
    result={'pdf_pages':len(pdf),'body_pages':15,'all_pages_rendered':True,'chapter_page_mapping_verified':True,'paragraphs_and_table_cells_preserved':True,'figure_sources_ascii_utf8':True,'figure_min_label_size_svg_px':20,'no_clipping_or_replacement_glyphs':True,'no_references_section_or_numbered_citations':True,'figures':figures,'page_inventory':page_inventory}
    (output/'validation.json').write_text(json.dumps(result,indent=2),encoding='utf-8',newline='\n')
    print(json.dumps({k:v for k,v in result.items() if k not in ['page_inventory','figures']}))

if __name__=='__main__':main()
