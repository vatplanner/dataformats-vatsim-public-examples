@echo off && setlocal EnableDelayedExpansion

rem Windows needs some special magic to wrap all arguments to pass them on as one _uninterpreted_ parameter...
rem see: https://stackoverflow.com/questions/357315/get-list-of-passed-arguments-in-windows-batch-script-bat

set args=%1
shift
:start
if [%1] == [] goto done
set args=%args% %1
shift
goto start
:done


echo WARNING: Providing regular expressions as parameters is tricky or maybe impossible on Windows.
echo          If you see such funny errors as 'The system cannot find the path specified.'
echo          or '^| was unexpected at this time' it's because your Windows shell tries to interpret
echo          those parameters on its own before they even reach this batch file. I recommend simply
echo          giving up in that case and using Git Bash or some other (pseudo-)Linux environment instead.
echo.

mvn compile exec:java -Dexec.mainClass=org.vatplanner.dataformats.vatsimpublic.examples.dump.Dump -Dexec.args="%args%"
