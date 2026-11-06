from android_smoke import *
assert SERIAL.startswith('emulator-')
def reach(title):
    for _ in range(9):
        if title in texts():return tap(title)
        adb('shell','input','swipe','540','1820','540','650','250')
    raise AssertionError(title)
adb('shell','am','force-stop','com.python.interviewcards');adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.5)
tap('Карточки');tap('Поиск по вопросам и ответам');adb('shell','input','text','==');adb('shell','input','keyevent','4');tap('В чём разница между == и is?');tap('✓  Запомнил');adb('shell','input','keyevent','4')
assert 'Запомненные · 1' in texts();reach('Режимы и тренажёры  →');reach('Проверить запомненное');reach('Показать ответ и пример');reach('Не ответил');assert 'Итог занятия' in texts()
reach('Выбрать другой режим');adb('shell','input','keyevent','4');assert 'Запомненные · 1' in texts()
print('PASS: bad self-assessment in voluntary review preserves LEARNED',flush=True)
reach('Режимы и тренажёры  →');reach('Проверить запомненное');reach('Показать ответ и пример');reach('Вернуть в обычное обучение');tap('Вернуть');reach('Выбрать другой режим');adb('shell','input','keyevent','4')
assert 'Запомненные · 0' in texts();print('PASS: only explicit confirmed return makes card NEW',flush=True)
