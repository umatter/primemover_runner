#!/usr/bin/python3
import requests

update_count = 0

resp = requests.get('http://localhost:35000/api/v2/profile')
if resp.status_code != 200:
    # This means something went wrong.
    raise ApiError('GET http://localhost:35000/api/v2/profile/ {}'.format(resp.status_code))
for profile in resp.json():
	if profile['name'].startswith('Comscore'):
		update_count+=1
		print('updating {} {}'.format(profile['uuid'], profile['name']))
		requests.post('http://localhost:35000/api/v2/profile/{}/update'.format(profile['uuid']))
		
    
print('updated {}'.format(update_count))