@echo off
echo Resetting Database (Deleting all data)...
docker-compose down -v
docker-compose up -d
echo Database reset complete.
pause