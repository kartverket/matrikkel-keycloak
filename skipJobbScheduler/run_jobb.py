import os
import requests
from urllib.parse import urlencode

TOKEN_ENDPOINT = os.environ.get('TOKEN_ENDPOINT')
JOB_ENDPOINT = os.environ.get('JOB_ENDPOINT')
CLIENT_ID = os.environ.get('CLIENT_ID')
CLIENT_SECRET = os.environ.get('CLIENT_SECRET')

def get_access_token(token_endpoint, client_id, client_secret):
    try:
        token_data = {
            'client_id': client_id,
            'client_secret': client_secret,
            'grant_type': 'client_credentials'
        }
        encoded_data = urlencode(token_data)

        headers = {'Content-Type': 'application/x-www-form-urlencoded'}

        response = requests.post(token_endpoint, data=encoded_data, headers=headers)
        response.raise_for_status()
        print('Access_token received')
        return response.json()['access_token']
    except requests.exceptions.RequestException as e:
        print('Error fetching token:', e)
        raise

def run_job(job_endpoint):
    print(f'Scheduled job started. Will trigger job on endpoint: {job_endpoint}')
    accessToken = get_access_token(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET)
    try:
        headers = {'Authorization': 'Bearer ' + accessToken, 'Content-Type': 'application/json'}
        response = requests.post(job_endpoint, headers=headers)
        response.raise_for_status()
        if response.status_code == 204:
            print(f'Scheduled job successfully triggered. Status_code: {response.status_code}')
        else:
            print(f'ERROR: Something went wrong. Status_code: {response.status_code}')
    except requests.exceptions.RequestException as e:
        print('Error running job: ', e)
        raise

def main():
    try:
        run_job(JOB_ENDPOINT)
    except Exception as e:
        print('An error occurred:', e)
        exit(1)

if __name__ == '__main__':
    main()
