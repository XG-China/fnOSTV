@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-source.ps1" %*
