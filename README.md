# Synthea Website

## Making Contributions:
For those with clinical or domain expertise, visit [our contribution page](https://github.com/synthetichealth/synthea/projects/1) to see a list of modules that need professional review. Developers can visit Synthea's [GitHub page](https://github.com/synthetichealth/synthea) to learn how to build and contribute to the project. Have any feedback on the current Synthea implementation? [Create an issue](https://github.com/synthetichealth/synthea/issues/new) on our github page, or [send us an email](mailto:synthea-list@groups.mitre.org).

## Downloading Project:
Before getting started on any development, one will need to have the following installed:

- [Git](https://git-scm.com/), our version controlling tool.
	- If you have never used git or github before, you should check out [this tutorial](https://try.github.io/levels/1/challenges/1) and/or [this reference sheet](http://gitref.org/index.html).
- [Ruby](https://www.ruby-lang.org/en/), the language that Bundler and Jekyll use.
- [Bundler](http://bundler.io/), which can be installed with `gem install bundler` once Ruby is available for use.
- [Jekyll](https://jekyllrb.com/), which can be installed with `gem install jekyll bundler`.
- For Windows users, [Cmder](http://cmder.net/) is a powerful command line emulator to serve as alternative to powershell. This has been helpful for me.

Once the technology stack is downloaded, you can follow [this command line guide](https://help.github.com/articles/fork-a-repo/) or [this desktop app guide](https://guides.github.com/activities/forking/) on forking, cloning your fork, pushing changes back to your repository and lodging pull requests on GitHub.

In the local version of your repository, run the following command to get all of the appropriate Ruby Gems downloaded.
```
$ bundle install
```
Finally, delete the CNAME file from your fork of the repository. This file will be added back later when you are making a pull request, but should be removed until then.


## Building the Project:
To run the project on localhost run:
```
bundle exec jekyll serve
```
To view the project on mobile browsers, you can serve the website up on your github project page by doing the following:

1. Push all of your changes to your github repository.
2. On the GitHub page for your fork, go to the settings tab and find the section for repository name.
3. Change repository name from `synthea.github.io` to `your-account-name.github.io` (yes, that means put your github account name there).
4. Make sure that the changes you want to view are on the master branch.
5. Open up the page `your-account-name.github.io` on the browser of your choice.


## Testing the Project:
After making changes to the website, one should run a local instance of the project and walk through the following checklist to test the website before integrating into production:

- [ ] Make sure your browser has been [hard-refreshed](http://refreshyourcache.com/en/cache/) to reflect new changes.
- [ ] View on Google Chrome.
- [ ] View on Safari.
- [ ] View on IE.
- [ ] View on Firefox.
- [ ] View on Android.
- [ ] View on IOS.
- [ ] Go through this [Section 508 Checklist](http://www.hhs.gov/web/section-508/making-files-accessible/checklist/html/index.html) to confirm compliance	
- [ ] Check colors used for [proper contrasting](http://webaim.org/resources/contrastchecker/) if they have been changed
- [ ] Perform accessibility testing using the [WAVE chrome extension](http://wave.webaim.org/extension/) and [Code Sniffer](http://squizlabs.github.io/HTML_CodeSniffer/).


## Pushing Your Project Changes:

1. Run browser tests mentioned above on localhost.
2. Push changes to your GitHub Project repository.
3. Run tests on mobile devices when `your-account-name.github.io` has been updated.
4. Replace the CNAME file you deleted when you first pulled the repository; the file should be named CNAME and have in it `yourdomainname.com/org/net`.
5. If everything checks out, [issue a pull request](https://help.github.com/articles/creating-a-pull-request-from-a-fork/) with the main repository.
6. Wait for someone in the collaborative to review your changes, merge to master, and see your contributions go live!
7. After your pull request has been merged, remove the CNAME file from your local repo again. Make sure to put this off until the changes have merged; pull requests continue to monitor changes you make even after you make the request.


## Technologies Used:
Below you will find a comprehensive list of the technologies and tools the Collaborative's website is built with, along with a brief description and links to wher you can learn more:

- [Ruby](https://www.ruby-lang.org/en/), the programming language needed for both bundle and jekyll to work, used to run bundler and manage all dependencies needed for the project.
- [Bundler](http://bundler.io/), a consistent environment for Ruby projects that tracks/installs needed gems, used to bundle together dependencies needed to run jekyll.
- [Jekyll](https://jekyllrb.com/), a static site generator that pairs neatly with Github, used to generate our static site locally and remotely on github.
- [SASS](http://sass-lang.com/), a powerful style sheet language, extending CSS to allow for variables, nesting and parametrized styles, used for simplifying and modularizing our style sheets.
- [Bootstrap Material Design](http://fezvrasta.github.io/bootstrap-material-design/), a material design inspired, bootstrap compatible styling library, used to style our HTML front end.
- [jQuery](https://jquery.com/), a JavaScript library enabling quick navigation and manipulation of HTML, used to interact with our website and make any dynamic changes.
- [FontAwesome](http://fontawesome.io/), a library of scalable vector icons that can instantly be customized, responsible for the icons we use on the page.


## About the Team Behind Synthea
### [The MITRE Corporation](https://www.mitre.org/)

The MITRE Corporation is a not-for-profit organization working in the public interest that operates federally funded research and development centers to provide innovative solutions to national problems.
