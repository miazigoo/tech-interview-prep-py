"""Bundle distributable APK and reproducible sources, excluding local caches and keys."""
import hashlib
from pathlib import Path
import shutil
from zipfile import ZipFile,ZIP_DEFLATED
ROOT=Path(__file__).resolve().parents[1]
out=ROOT/'releases';out.mkdir(exist_ok=True)
apk=out/'Python_Middle_5.0.apk'
shutil.copy2(ROOT/'android/app/build/outputs/apk/debug/app-debug.apk',apk)
paths=[ROOT/'README.md',ROOT/'TECH_SPEC.md',ROOT/'TECH_SPEC_V2.md',ROOT/'TECH_SPEC_V3.md',ROOT/'TECH_SPEC_V4.md',ROOT/'TECH_SPEC_V5.md',ROOT/'requirements.txt',ROOT/'requirements-lock.txt',ROOT/'.gitignore']
for folder in ['android','assets','audio','data','docs','scripts']:
    for p in (ROOT/folder).rglob('*'):
        if not p.is_file():continue
        relative=p.relative_to(ROOT)
        if any(x in ['build','.gradle','.kotlin','__pycache__'] for x in relative.parts):continue
        if p.name=='local.properties' or p.name.endswith('generation.log'):continue
        if p.suffix in ['.part','.hprof']:continue
        paths.append(p)
archive=out/'Python_Middle_Source_5.0.zip'
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
    for p in sorted(set(paths)): z.write(p,p.relative_to(ROOT))
with ZipFile(archive) as z:assert z.testzip() is None
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.file_digest(p.open('rb'),'sha256').hexdigest()+'  '+p.name+'\n' for p in [apk,archive]))
print(f'APK {apk.stat().st_size/1024**2:.2f} MiB; source {archive.stat().st_size/1024**2:.2f} MiB; {len(paths)} source files')
