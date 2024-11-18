import requests
from bs4 import BeautifulSoup
import json

url = "https://f-1world.ru/turnirnaja-tablica/5921-formula-1-turnirnaja-tablica-pilotov-sezon-2024-goda.html"

page = requests.get(url)

soup = BeautifulSoup(page.text, "lxml")

table = soup.find('table', class_='fr-solid-borders fr-alternate-rows')

rows = table.find_all('tr')[1:]

teams_data = []

for row in rows:
    name = row.find_all('td')[1].text.strip()
    team = row.find_all('td')[2].text.strip()
    wins = row.find_all('td')[3].text.strip()
    pl = row.find_all('td')[4].text.strip()
    lk = row.find_all('td')[5].text.strip()
    points = row.find_all('td')[6].text.strip()
    teams_data.append({
        'Имя': name,
        'Название команды': team,
        'ПОБ': wins,
        'ПЛ': pl,
        'ЛК': lk,
        'Очки': points
    })

with open('table.json', 'w', encoding='utf-8') as json_file:
    json.dump(teams_data, json_file, ensure_ascii=False, indent=4)

print("Данные успешно записаны в файл tablepilots.json")