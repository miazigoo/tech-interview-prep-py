"""Create a deduplicated catalogue from the exact on-screen explanations and analogies."""
import json,re,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'android/app/src/main/assets/cards.json';deck=json.loads(p.read_text())
s=(ROOT/'android/app/src/main/java/com/python/interviewcards/LearningHints.kt').read_text().split('fun mistake')[0]
hints=dict(re.findall(r'"(\d{2})"->"([^"\n]+)"',s))
tracks={}
for card in deck['cards']:
    values={'explanation':'Объяснение простыми словами. '+(card.get('plain_explanation') or card['example_note']),
            'analogy':('Аналогия. '+card['analogy']) if card.get('analogy') else 'Аналогия раздела. '+hints[card['category_id']]}
    for role,text in values.items():
        key=hashlib.sha256(text.encode()).hexdigest()[:20];path=f'audio/understand_{key}.mp3'
        card[role+'_audio']=path
        tracks[key]=dict(id=key,text=text,path=path)
(ROOT/'data/understanding.json').write_text(json.dumps({'tracks':list(tracks.values())},ensure_ascii=False,indent=2)+'\n')
p.write_text(json.dumps(deck,ensure_ascii=False,indent=2)+'\n')
print('Cards',len(deck['cards']),'unique explanation/analogy tracks',len(tracks))
