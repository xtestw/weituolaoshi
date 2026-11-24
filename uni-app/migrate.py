"""One-time converter for the native WeChat pages. Re-run after native changes."""
from pathlib import Path
import json, re, shutil

ROOT = Path(__file__).resolve().parent / 'src'
SOURCE = ROOT.parent.parent / 'wechat-miniprogram'

def matching(s, start, left='{', right='}'):
    depth = 0; quote = None; escape = False; comment = None
    for i in range(start, len(s)):
        c = s[i]; n = s[i:i+2]
        if comment == '//':
            if c == '\n': comment = None
            continue
        if comment == '/*':
            if n == '*/': comment = None
            continue
        if quote:
            if escape: escape = False
            elif c == '\\': escape = True
            elif c == quote: quote = None
            continue
        if n in ('//','/*'): comment = n; continue
        if c in ('"',"'",'`'): quote = c; continue
        if c == left: depth += 1
        elif c == right:
            depth -= 1
            if depth == 0: return i
    raise ValueError('unclosed bracket')

def split_top(s):
    result=[]; start=0; depth=0; quote=None; escape=False
    for i,c in enumerate(s):
        if quote:
            if escape: escape=False
            elif c=='\\': escape=True
            elif c==quote: quote=None
            continue
        if c in ('"',"'",'`'): quote=c; continue
        if c in '{[(': depth+=1
        elif c in '}])': depth-=1
        elif c==',' and depth==0: result.append(s[start:i].strip()); start=i+1
    result.append(s[start:].strip())
    return [x for x in result if x]

def imports(s):
    return re.sub(r"const\s+\{([^}]+)\}\s*=\s*require\(['\"]([^'\"]+)['\"]\)", r"import {\1} from '\2'", s)

def script(src, component=False):
    src=imports(src).replace('wx.', 'uni.').replace('this.data.', 'this.').replace('this.data', 'this').replace('uni.nextTick(', 'this.$nextTick(')
    src=re.sub(r"if \(typeof this\.getTabBar === 'function' && this\.getTabBar\(\)\) this\.getTabBar\(\)\.setData\(\{[^\n}]*hidden: true[^\n}]*\}\)", 'uni.hideTabBar()', src)
    src=re.sub(r"if \(typeof this\.getTabBar === 'function' && this\.getTabBar\(\)\) this\.getTabBar\(\)\.setData\(\{[^\n}]*hidden: false[^\n}]*\}\)", 'uni.showTabBar()', src)
    src=re.sub(r"if \(typeof this\.getTabBar === 'function' && this\.getTabBar\(\)\) this\.getTabBar\(\)\.setData\(\{[^\n}]*\}\)", '', src)
    prefix, body = src.split('Component({' if component else 'Page({',1)
    body=body.rsplit('})',1)[0]
    props=split_top(body)
    out=[]; methods=[]
    for p in props:
        if p.startswith('data:'):
            out.append('data() { return '+p[5:].strip()+' }')
        elif p.startswith('properties:'):
            entries=[]
            for x in split_top(p[p.index('{')+1:p.rindex('}')]):
                key,val=x.split(':',1)
                val=val.replace('value:', 'default:')
                entries.append(key+':'+val)
            out.append('props: {'+', '.join(entries)+'}')
        elif p.startswith('methods:'):
            methods+=split_top(p[p.index('{')+1:p.rindex('}')])
        elif re.match(r'on(Load|Show|Hide|Unload|PullDownRefresh|ReachBottom|ShareAppMessage)\s*\(',p):
            out.append(p)
        else: methods.append(p)
    methods.append('setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }')
    if component:
        methods=[m.replace("this.triggerEvent('open',", "this.$emit('open',") for m in methods]
    out.append('methods: {\n'+',\n'.join(methods)+'\n}')
    return prefix+'export default {\n'+',\n'.join(out)+'\n}\n'

def expr(value):
    parts=re.split(r'(\{\{.*?\}\})',value,flags=re.S)
    if len(parts)==3 and not parts[0] and not parts[2]: return parts[1][2:-2].strip()
    return ' + '.join(('('+p[2:-2].strip()+')' if p.startswith('{{') else json.dumps(p,ensure_ascii=False)) for p in parts if p)

def template(src):
    src=src.replace('<block', '<template').replace('</block>', '</template>')
    src=re.sub(r'\{\{\s*([\s\S]*?)\s*\}\}',lambda m:'{{ '+m.group(1).strip()+' }}',src)
    # Convert one opening tag at a time; quoted > signs remain inside the tag.
    def tag(m):
        raw=m.group(0)
        if raw.startswith('</') or raw.startswith('<!--'): return raw
        attrs=re.findall(r'''([:@\w-]+)(?:\s*=\s*("[^"]*"|'[^']*'))?''',raw)
        if not attrs: return raw
        name=attrs[0][0]; result=[]; loop=None; key=None
        for a,quoted in attrs[1:]:
            if not a or a=='/': continue
            val=quoted[1:-1] if quoted else None
            if a=='wx:for': loop=expr(val); continue
            if a=='wx:key': key=val; continue
            if a=='wx:if': result.append('v-if="'+expr(val)+'"'); continue
            if a=='wx:elif': result.append('v-else-if="'+expr(val)+'"'); continue
            if a=='wx:else': result.append('v-else'); continue
            if a.startswith('bind:'): result.append('@'+a[5:]+'="'+val+'"'); continue
            if a.startswith('bind'): result.append('@'+a[4:]+'="'+val+'"'); continue
            if a.startswith('catch'): result.append('@'+a[5:]+'.stop="'+val+'"'); continue
            if val is None: result.append(a); continue
            if '{{' in val:
                result.append(':'+a+'="'+expr(val).replace('"','&quot;')+'"')
            else: result.append(a+'='+quoted)
        if loop:
            result.insert(0,'v-for="(item, index) in '+loop+'"')
            if key and key!='*this': result.insert(1,':key="item.'+key+'"')
            elif key=='*this': result.insert(1,':key="item"')
        return '<'+name+(' '+' '.join(result) if result else '')+('/>' if raw.rstrip().endswith('/>') else '>')
    src=re.sub(r'</?[A-Za-z][A-Za-z0-9-]*(?:"[^"]*"|\'[^\']*\'|[^\'">])*?>',tag,src)
    return src

def convert(src, dst, component=False):
    dst.parent.mkdir(parents=True,exist_ok=True)
    tpl=template(src.with_suffix('.wxml').read_text()).replace('/assets/', '/static/assets/')
    js=script(src.with_suffix('.js').read_text(),component)
    if not component:
        names=[]
        for name in ('service-card','b-tabbar'):
            if '<'+name in tpl:
                ident=''.join(x.capitalize() for x in name.split('-'))
                js="import "+ident+" from '../../components/"+name+"/index.vue'\n"+js
                names.append(ident)
        if names: js=js.replace('export default {','export default {\ncomponents: { '+', '.join(names)+' },',1)
    css=src.with_suffix('.wxss').read_text()
    dst.write_text('<template>\n'+tpl+'\n</template>\n<script>\n'+js+'</script>\n<style>\n'+css+'\n</style>\n')

for js in (SOURCE/'pages').glob('*/index.js'):
    convert(js.with_suffix(''),ROOT/'pages'/js.parent.name/'index.vue')
for js in (SOURCE/'components').glob('*/index.js'):
    convert(js.with_suffix(''),ROOT/'components'/js.parent.name/'index.vue',True)
for folder in ('utils','services'):
    shutil.copytree(SOURCE/folder,ROOT/folder,dirs_exist_ok=True)
    for js in (ROOT/folder).glob('*.js'):
        if js.name.endswith('.test.js'): js.unlink(); continue
        s=imports(js.read_text()).replace('wx.','uni.')
        s=re.sub(r'module\.exports\s*=\s*\{([^}]+)\}',r'export {\1}',s)
        js.write_text(s)
