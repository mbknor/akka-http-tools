akka-http-tools
======================
[![Build Status](https://travis-ci.org/mbknor/akka-http-tools.svg)](https://travis-ci.org/mbknor/akka-http-tools)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.kjetland/akka-http-tools_2.12/badge.svg)](http://search.maven.org/#search%7Cga%7C1%7Cakka-http-tools)


Contains a set of utilities useful when implementing REST-applications
using akka-http



--------
How to modify auth0 rule to include permissions when using *Authorization extention*
-------


    // Custom 
    const namespace = 'https://yourdomain.com/claims/';
    context.idToken[namespace + 'groups'] = data.groups;
    context.idToken[namespace + 'roles'] = data.roles;
    context.idToken[namespace + 'permissions'] = data.permissions;



