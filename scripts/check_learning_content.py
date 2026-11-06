"""Check source preservation, packaged audio hashes and executable Python examples."""
import ast,hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
source=json.loads((ROOT/'data/cards.json').read_text())['cards']
app=json.loads((ROOT/'android/app/src/main/assets/cards.json').read_text())['cards']
assert len(app)==155 and len({c['id'] for c in app})==155
compiled=[]
originals={c['id']:c for c in app}
for a in source:
    b=originals[a['id']]
    for field in ['id','question','answer','question_audio','answer_audio','question_speech','answer_speech']:
        assert a[field]==b[field],(a['id'],field)
    if a['example']: assert a['example']==b['example'],a['id']
    assert b['example'] and b['example_note'] and b['self_check'],b['id']
    for role in ['question_audio','answer_audio']:
        original=ROOT/b[role];asset=ROOT/'android/app/src/main/assets'/b[role]
        assert hashlib.sha256(original.read_bytes()).digest()==hashlib.sha256(asset.read_bytes()).digest(),b['id']
    code=b['example']
    if code.startswith(('import ','from ','def ','class ','assert ','items =','a =','point =','text =','events =','cache =','data =','names =','allowed =','lookup =','values =','g =','eager =','try:','bad =','@','qs =')):
        ast.parse(code);compiled.append(b['source_number'])
# Explicitly selected examples are standalone, use only stdlib, and have no external effects.
runnable=[1,2,3,5,7,8,9,10,12,13,14,15,16,18,19,20,21,22,23,25,26,29,30,33,34,35,37,38,39,40,42,43,47,49,53,78,106,107,108,109,110,111,112,113,114]
for number in runnable:
    result=subprocess.run([sys.executable,'-I','-c',app[number-1]['example']],capture_output=True,text=True,timeout=5)
    assert result.returncode==0,(number,result.stderr)
for c in app[115:]:
    assert all(c[k] for k in ["example","example_note","self_check","plain_explanation","common_mistake"])
    for role in ["question_audio","answer_audio"]:
        assert (ROOT/c[role]).read_bytes()==(ROOT/"android/app/src/main/assets"/c[role]).read_bytes()
understanding=json.loads((ROOT/'audio/understanding-manifest.json').read_text())
assert understanding['complete'] and len(understanding['files'])==205
seen=set()
for card in app:
    for role in ['explanation_audio','analogy_audio']:
        path=card[role];seen.add(path)
        assert (ROOT/path).read_bytes()==(ROOT/'android/app/src/main/assets'/path).read_bytes()
        assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==understanding['files'][Path(path).name]['sha256']
assert len(seen)==205
result=dict(cards=155,complete_examples=155,source_examples_preserved=25,audio_assets_match=515,new_understanding_tracks=205,python_examples_parsed=len(compiled),standalone_examples_executed=len(runnable))
print(json.dumps(result,ensure_ascii=False,indent=2))
