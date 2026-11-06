from android_smoke import *
assert SERIAL.startswith('emulator-')
adb('shell','input','keyevent','224');adb('shell','wm','dismiss-keyguard')
adb('shell','am','force-stop','com.python.interviewcards')
adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.5)
# Starting is a visible user action, as required by modern Android foreground-service rules.
for _ in range(6):
 if '▶  Слушать без остановки' in texts():break
 adb('shell','input','swipe','540','1800','540','700','250')
tap('▶  Слушать без остановки');time.sleep(1)
adb('shell','input','keyevent','3');adb('shell','input','keyevent','223');time.sleep(2)
services=adb('shell','dumpsys','activity','services','com.python.interviewcards').decode()
assert 'PlaybackService' in services and 'isForeground=true' in services
media=adb('shell','dumpsys','media_session').decode();assert 'Middle' in media and 'PlaybackState' in media
assert 'state=3' in media or 'state=PLAYING(3)' in media
print('PASS: foreground media service remains active after Home and screen off',flush=True)
adb('shell','cmd','media_session','dispatch','pause');time.sleep(.5)
media=adb('shell','dumpsys','media_session').decode();assert 'state=2' in media or 'state=PAUSED(2)' in media
print('PASS: MediaSession pause command',flush=True)
adb('shell','cmd','media_session','dispatch','play');time.sleep(.5)
media=adb('shell','dumpsys','media_session').decode();assert 'state=3' in media or 'state=PLAYING(3)' in media
adb('shell','cmd','media_session','dispatch','next');time.sleep(.5)
print('PASS: MediaSession play and next commands with screen off',flush=True)
adb('shell','input','keyevent','224');adb('shell','wm','dismiss-keyguard')
adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.4)
shot('v2_radio')
for _ in range(5):
 if 'Стоп' in texts():break
 adb('shell','input','swipe','540','1800','540','700','250')
tap('Стоп');time.sleep(.4)
services=adb('shell','dumpsys','activity','services','com.python.interviewcards').decode();assert 'isForeground=true' not in services
print('PASS: stop removes foreground service',flush=True)
