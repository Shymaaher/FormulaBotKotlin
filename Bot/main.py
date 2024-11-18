
import requests
from bs4 import BeautifulSoup
import json

url = "https://f-1world.ru/kubok-konstruktorov/5922-turnirnaja-tablica-kubka-konstruktorov-2024-goda.html"

page = requests.get(url)

soup = BeautifulSoup(page.text, "lxml")

table = soup.find('table', class_='fr-solid-borders fr-alternate-rows')

rows = table.find_all('tr')[1:]

teams_data = []

for row in rows:
    team_name = row.find_all('td')[1].text.strip()
    wins = row.find_all('td')[2].text.strip()
    pl = row.find_all('td')[3].text.strip()
    lk = row.find_all('td')[4].text.strip()
    points = row.find_all('td')[5].text.strip()
    teams_data.append({
        'Команда': team_name,
        'Поб': wins,
        'ПЛ': pl,
        'ЛК': lk,
        'Очки': points
    })

with open('teams.json', 'w', encoding='utf-8') as json_file:
    json.dump(teams_data, json_file, ensure_ascii=False, indent=4)

print("Данные успешно записаны в файл teams.json")

