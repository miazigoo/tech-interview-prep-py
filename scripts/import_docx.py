"""Import styled source paragraphs; document text is treated strictly as data."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
NS = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
W = '{' + NS['w'] + '}'

def import_cards(source):
    with ZipFile(source) as z:
        body = ET.fromstring(z.read('word/document.xml')).find('w:body', NS)
    cards, current, category = [], None, None
    for p in body.findall('w:p', NS):
        text = ''.join((n.text or '') if n.tag == W+'t' else '\n' if n.tag == W+'br' and n.get(W+'type') != 'page' else '\t' if n.tag == W+'tab' else '' for n in p.iter())
        style_node = p.find('w:pPr/w:pStyle', NS)
        style = style_node.get(W+'val') if style_node is not None else ''
        if style == 'Heading1':
            match = re.fullmatch(r'(\d{2})\s+(.+)', text.strip())
            current = None
            if match:
                category = match.groups()
            continue
        match = re.fullmatch(r'(\d{3})\.\s+(.+)', text.strip())
        if style == 'Heading2' and match:
            number = int(match[1])
            assert category, text
            current = dict(id=f'py-middle-{number:03}', source_number=number, category_id=category[0], category=category[1], question=match[2], answer='', example=None, example_note=None, content_status='imported', question_audio=f'audio/py-middle-{number:03}_question.mp3', answer_audio=f'audio/py-middle-{number:03}_answer.mp3')
            cards.append(current)
        elif current and style == 'Answer':
            current['answer'] += ('\n' if current['answer'] else '') + re.sub(r'^Ответ\.\s*', '', text)
        elif current and style == 'Codesample':
            current['example'] = (current['example']+'\n\n' if current['example'] else '') + text
    assert [c['source_number'] for c in cards] == list(range(1,116)), 'Expected 115 sequential questions'
    assert all(c['question'] and c['answer'] for c in cards)
    return dict(schema_version=1, deck_id='python-middle-ru', title='Python Middle — техническое собеседование', source_file=source.name, source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(), cards=cards)

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);args=p.parse_args()
    deck=import_cards(args.source)
    out=ROOT/'data/cards.json';out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(deck,ensure_ascii=False,indent=2)+'\n')
    print(f"Imported {len(deck['cards'])} cards; {sum(bool(c['example']) for c in deck['cards'])} source examples")
