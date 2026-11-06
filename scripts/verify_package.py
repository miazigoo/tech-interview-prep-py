"""Verify the entire audio package, source coverage and code preservation."""
import hashlib
import json
from pathlib import Path
import sys
from generate_audio import inspect_audio, digest, VOICE, RATE, NORMALIZER_VERSION
from import_docx import import_cards
import edge_tts

ROOT=Path(__file__).resolve().parents[1]
deck=json.loads((ROOT/'data/cards.json').read_text())
manifest=json.loads((ROOT/'audio/manifest.json').read_text())
source=Path(sys.argv[1]) if len(sys.argv)>1 else None
assert len(deck['cards'])==115
assert len({c['id'] for c in deck['cards']})==115
assert len({c['category_id'] for c in deck['cards']})==10
if source:
    fresh=import_cards(source)
    assert fresh['source_sha256']==deck['source_sha256']
    for a,b in zip(fresh['cards'],deck['cards']):
        for field in ['id','question','answer','example','category']:
            assert a[field]==b[field], (a['id'],field)
expected=set()
seconds=0
for card in deck['cards']:
    for role in ('question','answer'):
        path=ROOT/card[role+'_audio'];expected.add(path.name)
        record=manifest['files'][path.name]
        assert record['card_id']==card['id'] and record['role']==role
        assert record['speech_text']==card[role+'_speech']
        assert record['source_text_sha256']==digest(card[role])
        assert record['speech_sha256']==digest(card[role+'_speech'])
        assert record['audio_sha256']==hashlib.sha256(path.read_bytes()).hexdigest()
        assert record['fingerprint']==digest(json.dumps([card[role+'_speech'],VOICE,RATE,NORMALIZER_VERSION,edge_tts.__version__]))
        duration=inspect_audio(path)
        assert abs(duration-record['duration_seconds'])<0.002
        seconds+=duration
assert expected==set(manifest['files'])
assert expected=={p.name for p in (ROOT/'audio').glob('py-middle-*.mp3')}
assert manifest['complete']
print(json.dumps(dict(cards=115,topics=10,audio_files=len(expected),source_examples=sum(bool(c['example']) for c in deck['cards']),duration_minutes=round(seconds/60,2),audio_megabytes=round(sum((ROOT/'audio'/n).stat().st_size for n in expected)/1024**2,2),source_preservation_checked=bool(source)),ensure_ascii=False,indent=2))
