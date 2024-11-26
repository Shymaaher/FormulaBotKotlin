import requests
from bs4 import BeautifulSoup
import pandas as pd

url = 'https://f-1world.ru/kubok-konstruktorov/5922-turnirnaja-tablica-kubka-konstruktorov-2024-goda.html'

page = requests.get(url)

soup = BeautifulSoup(page.text, 'lmxl')