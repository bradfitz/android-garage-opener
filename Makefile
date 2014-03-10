env:
	docker build -t danga/garagebuild devenv

dockerdebug:
	docker run -v /home/bradfitz/src/github.com/bradfitz/android-garage-opener:/src/android-garage-opener danga/garagebuild /src/android-garage-opener/build-in-docker.pl debug

dockerrelease:
	docker run -t -i -v /home/bradfitz/src/github.com/bradfitz/android-garage-opener:/src/android-garage-opener -v $(HOME)/keys/android-garage:/keys danga/garagebuild /src/android-garage-opener/build-in-docker.pl release
