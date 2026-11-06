from android_smoke import *
assert SERIAL.startswith('emulator-'), 'Test emulator only'
def reach(title):
    for _ in range(9):
        if title in texts():return tap(title)
        adb('shell','input','swipe','540','1820','540','650','250')
    raise AssertionError('Not found '+title)
adb('shell','pm','clear','com.python.interviewcards')
adb('shell','pm','grant','com.python.interviewcards','android.permission.POST_NOTIFICATIONS')
adb('shell','am','start','-n','com.python.interviewcards/.MainActivity');time.sleep(.6)
assert any('155 карточек' in t for t in texts());shot('v2_home')
reach('Режимы и тренажёры  →');shot('v2_modes')
reach('Решать Python-задачи');assert 'Что выведет код?' in texts();tap('Проверить предположение');assert 'Результат' in texts();shot('v2_code');reach('Я ответил верно')
assert any('2 из 16' in t for t in texts());adb('shell','input','keyevent','4')
reach('Тренироваться в SQL');reach('Показать решение');reach('Выполнить и проверить');time.sleep(.3)
assert any('✓ Верно' in t for t in texts());shot('v2_sql')
adb('shell','input','keyevent','4');reach('20 карточек');reach('Показать ответ и пример');reach('Далее без оценки');reach('Завершить занятие');assert 'Итог занятия' in texts();shot('v2_summary')
reach('Выбрать другой режим');reach('Проверить запомненное');assert any('пока нет доступных' in t for t in texts());reach('Выбрать другой режим')
reach('Начать собеседование');tap('30 секунд');shot('v2_interview')
print('PASS: code, SQL, finite session summary, empty review, interview launch',flush=True)
# Let the real question audio finish and the real 30-second timer expire.
for _ in range(30):
    if 'Как ответить' in texts():break
    time.sleep(1)
assert 'Как ответить' in texts();shot('v2_interview_answer')
print('PASS: timed interview automatically reveals answer',flush=True)
reach('Ответил');assert any('2 из 10' in t for t in texts());reach('Завершить занятие');assert any('Ответил: 1' in t for t in texts())
print('PASS: interview assessment counted',flush=True)
