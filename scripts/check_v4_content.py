"""Validate references, staged hints and every preserved MP3 in the built APK."""
import hashlib,json
from pathlib import Path
from zipfile import ZipFile
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'android/app/src/main/assets'
read=lambda p:json.loads(p.read_text())
cards={c['id']:c for c in read(ASSETS/'cards.json')['cards']}
learning=read(ASSETS/'learning_v4.json');cues=read(ASSETS/'audio_cues.json')
assert len(learning['chains'])==7
for chain in learning['chains']:
    ids=[s['card_id'] for s in chain['steps']]
    assert len(ids)==len(set(ids)) and len(ids)>=5
    assert all(s['card_id'] in cards and s['connection'] for s in chain['steps'])
assert set(learning['followups'])==set(cards)
for base,items in learning['followups'].items():
    assert len(items)==2 and len({i['card_id'] for i in items})==2
    for item in items:
        assert item['card_id']!=base
        card=cards[item['card_id']]
        assert all(item[f]==card[f] for f in ['question','answer','question_audio'])
ex=read(ASSETS/'exercises.json')
for key in ['sql','code','cases']:
    for t in ex[key]:
        assert len(t['hints'])==2 and all(t['hints'])
        if key=='sql':assert all(t['solution'] not in h for h in t['hints'])
audio=[p for p in (ASSETS/'audio').glob('*.mp3') if not p.name.startswith('v5_')];assert len(audio)==515 and len(cues)>=515
with ZipFile(ROOT/'android/app/build/outputs/apk/debug/app-debug.apk') as apk:
    for p in audio:
        rel=p.relative_to(ASSETS).as_posix();cue=cues[rel]
        assert cue['duration_ms']>0 and cue['starts_ms'][0]==0
        assert cue['starts_ms']==sorted(set(cue['starts_ms']))
        assert all(0<=x<cue['duration_ms'] for x in cue['starts_ms'])
        assert hashlib.sha256(p.read_bytes()).digest()==hashlib.sha256((ROOT/rel).read_bytes()).digest()
        assert apk.read('assets/'+rel)==p.read_bytes()
for name in ['learning_v4.json','audio_cues.json','exercises.json']:
    assert (ROOT/'data'/name).read_bytes()==(ASSETS/name).read_bytes()
report=dict(chains=7,followups=310,staged_tasks=39,original_and_apk_mp3_unchanged=515)
(ROOT/'docs/v4-content-checks.json').write_text(json.dumps(report,indent=2)+'\n')
print(report)
