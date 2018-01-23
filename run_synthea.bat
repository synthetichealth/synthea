@echo off
@rem ##########################################################################
@rem
@rem  Synthea launcher for Windows
@rem
@rem ##########################################################################
setlocal EnableDelayedExpansion

IF "%~1" == "" (
  @rem Just run Synthea with no args
  gradlew.bat run
  
) ELSE (
  @rem Running Synthea with arguments
  @rem For simplicity, do nothing and just pass the args to gradle
  SET syntheaArgs=^[
  echo step1
  for %%x in (%*) do (
    echo loop %%x
    SET syntheaArgs=!syntheaArgs!'%%~x',
  ) 
  SET syntheaArgs=!syntheaArgs!^]
  @rem Trailing comma ok, don't need to remove it
  gradlew.bat run -PappArgs="!syntheaArgs!"
)
