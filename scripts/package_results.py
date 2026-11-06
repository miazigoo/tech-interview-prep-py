"""Package already verified content; run verify_package.py first."""
import json
from pathlib import Path
import subprocess
from zipfile import ZipFile,ZIP_DEFLATED
from datetime import datetime

root=Path(__file__).resolve().parents[1]
m=json.loads((root/'audio/manifest.json').read_text())
assert m.get('complete') and len(m['files'])==230
minutes=sum(f['duration_seconds'] for f in m['files'].values())/60
megabytes=sum(f['bytes'] for f in m['files'].values())/1024**2
subprocess.run(['ffmpeg','-y','-v','error','-i',str(root/'audio/py-middle-002_question.mp3'),'-i',str(root/'audio/py-middle-002_answer.mp3'),'-filter_complex','[0:a]apad=pad_dur=2[q];[q][1:a]concat=n=2:v=0:a=1[out]','-map','[out]','-codec:a','libmp3lame','-b:a','96k',str(root/'voice_preview.mp3')],check=True)
(root/'docs/AUDIO_REPORT.md').write_text(f'''# Отчёт о подготовке озвучки

Дата завершения: 2 ноября 2026 года.

Готово 230 отдельных MP3: 115 вопросов и 115 полных ответов. Голос — мужской en-US-AndrewMultilingualNeural, темп -8%, pitch +0Hz. Русская речь и произносительная нормализация технических терминов. Это тот же выбранный голос, что в проекте English. Сервис Microsoft Edge TTS, клиент edge-tts {m['client_version']}.

Общая длительность без пауз между треками: {minutes:.1f} минут. Размер аудиофайлов: {megabytes:.1f} МиБ. Технический формат исходных треков: MP3 24 kHz 48 kbps mono.

## Что проверено

- Все 115 последовательных id, 10 тем и 230 ожидаемых путей присутствуют.
- Исходные вопросы, ответы и 25 примеров кода сверены с DOCX без изменений; переносы и отступы сохранены.
- Каждый файл полностью декодирован ffmpeg без ошибок; ffprobe вернул положительную длительность.
- SHA256 каждого MP3, исходного текста и speech-текста совпадает с manifest.json.
- Все файлы соответствуют одному голосу, темпу и версии нормализатора; повторов внутри треков нет в тексте синтеза.

Автоматическая проверка подтверждает целостность и полноту, но не оценивает приятность тембра, каждое ударение или произношение на слух. Прослушивание человеком всего пакета не проводилось. Для самостоятельной оценки сохранён voice_preview.mp3: вопрос о == и is, двухсекундная пауза и ответ.

## Особенности генерации

Первоначальная проба ru-RU-DmitryNeural давала частые NoAudioReceived; пакет полностью пересоздан Andrew Multilingual. Временные отказы обрабатывались повторными попытками, пустые результаты не принимались. [Описание аналогичной ошибки в проекте edge-tts](https://github.com/rany2/edge-tts/issues/512).

Вопросы и ответы записаны раздельно, по одному произнесению в каждом файле. Ровно x1–x5, паузы и автопереход должны реализовываться в Android-плеере. Аудиофайлы не содержат программный код примеров: он предназначен для отображения.

## Что ещё требуется для приложения

Android-проект и APK не входят в этот подготовительный этап. В исходном DOCX 25 примеров на 115 вопросов; ещё 90 примеров, пояснения и вопросы самопроверки предстоит подготовить до выпуска. Технический аудит содержания исходных ответов не выполнялся.
''')
paths=[root/'TECH_SPEC.md',root/'README.md',root/'requirements.txt',root/'requirements-lock.txt',root/'voice_preview.mp3']
for folder,glob in [('docs','*.md'),('scripts','*.py'),('data','*.json'),('audio','*.mp3')]:
    paths.extend(sorted((root/folder).glob(glob)))
paths.append(root/'audio/manifest.json')
paths.append(root/'docs/verification.json')
with ZipFile(root/'Python_Interview_Preparation.zip','w',ZIP_DEFLATED) as z:
    for p in paths:z.write(p,p.relative_to(root))
print(f'Packed {len(paths)} files; {minutes:.1f} min; {megabytes:.1f} MiB audio')
