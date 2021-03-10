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
  SET syntheaArgs= 

  :loop
  SET syntheaArgs=!syntheaArgs!'%1',
  @rem Trailing comma ok, don't need to remove it
  shift
  if not "%~1"=="" goto loop

  gradlew.bat run -Params="[!syntheaArgs!]"
)
