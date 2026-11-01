"""Fetch the official Qwen ModelScope mirror; verify every downloaded SHA256."""
from pathlib import Path
import urllib.request,urllib.parse,json,hashlib,time,concurrent.futures,argparse,shutil
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/archmage-tts/model'
OP=urllib.request.build_opener(urllib.request.ProxyHandler({}))
BASE='https://modelscope.cn/api/v1/models/Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice/repo'

def fetch(entry):
    if entry['Type']!='blob' or entry['Path']=='.gitattributes':return
    target=OUT/entry['Path'];target.parent.mkdir(parents=True,exist_ok=True)
    shared=ROOT/'build/archmage-tts/model'/entry['Path']
    if OUT.name!='model' and shared.is_file() and shared.stat().st_size==entry['Size'] and not target.exists():
        digest=hashlib.sha256()
        with shared.open('rb') as existing:
            while chunk:=existing.read(1024*1024):digest.update(chunk)
        if digest.hexdigest()==entry['Sha256']:
            shutil.copy2(shared,target);print('verified shared',entry['Path'],flush=True);return
    if target.exists() and target.stat().st_size==entry['Size']:
        with target.open('rb') as existing:
            digest=hashlib.sha256()
            while chunk:=existing.read(1024*1024):digest.update(chunk)
        if digest.hexdigest()==entry['Sha256']:return
    temp=target.with_suffix(target.suffix+'.part')
    for attempt in range(4):
        try:
            url=BASE+'?'+urllib.parse.urlencode({'Revision':'master','FilePath':entry['Path']})
            last=time.time();size=0;digest=hashlib.sha256()
            with OP.open(url,timeout=60) as response,temp.open('wb') as output:
                while chunk:=response.read(1024*1024):
                    output.write(chunk);digest.update(chunk);size+=len(chunk)
                    if time.time()-last>20:
                        print(entry['Path'],round(size/entry['Size']*100,1),'%',flush=True);last=time.time()
            assert size==entry['Size'] and digest.hexdigest()==entry['Sha256'],'Model digest mismatch'
            temp.replace(target);print('verified',entry['Path'],size,flush=True);return
        except Exception as error:
            print('retry',entry['Path'],attempt,repr(error),flush=True)
    raise RuntimeError('Unable to fetch '+entry['Path'])

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--flavor',choices=['CustomVoice','Base','VoiceDesign'],default='CustomVoice');args=parser.parse_args()
    if args.flavor!='CustomVoice':
        OUT=ROOT/('build/archmage-tts/model-'+args.flavor.lower())
        BASE='https://modelscope.cn/api/v1/models/Qwen/Qwen3-TTS-12Hz-1.7B-'+args.flavor+'/repo'
    listing=json.loads(OP.open(BASE+'/files?Revision=master&Recursive=true',timeout=30).read())
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'source-files.json').write_text(json.dumps(listing,indent=2),encoding='utf8')
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(fetch,listing['Data']['Files']))
