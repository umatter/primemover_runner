#!/usr/bin/python3

import boto3
import botocore
import sys

print("Number of arguments:", len(sys.argv), "arguments.")
print("Argument List:", str(sys.argv))

bucket_name = "primemoverrunner"
prefix      = "reports/"


def listFiles():
	s3_conn   = client('s3')  # type: BaseClient  ## again assumes boto.cfg setup, assume AWS S3
	s3_result =  s3_conn.list_objects_v2(Bucket=bucket_name, Prefix=prefix, Delimiter = "/")
	
	if 'Contents' not in s3_result:
	    print(s3_result)
	    return []
	
	file_list = []
	for key in s3_result['Contents']:
	    file_list.append(key['Key'])
	print(f"List count = {len(file_list)}")
	
	while s3_result['IsTruncated']:
	    continuation_key = s3_result['NextContinuationToken']
	    s3_result = s3_conn.list_objects_v2(Bucket=bucket_name, Prefix=prefix, Delimiter="/", ContinuationToken=continuation_key)
	    for key in s3_result['Contents']:
	        file_list.append(key['Key'])
	    print(f"List count = {len(file_list)}")
	return file_list


def loadFile():
	targetFile = sys.argv[2]
	s3 = boto3.resource('s3')
	
	try:
	    s3.Bucket(bucket_name).download_file(targetFile, "temp.zip")
	except botocore.exceptions.ClientError as e:
	    if e.response['Error']['Code'] == "404":
	        print("The object does not exist.")
	    else:
	        raise
	print("finished downloading file")
if (len(sys.argv) <= 1):
	print("no function provided")	
elif (sys.argv[1] == "listFiles"): 
	res = listFiles()
	print (res)
elif (sys.argv[1] == "loadFile"):
	loadFile()
else:
	print("no command recognized")