FROM nginx:latest

RUN rm -rf /usr/share/nginx/html/*

COPY output/fhir/* /usr/share/nginx/html/

COPY nginx.conf /etc/nginx/conf.d/default.conf.template

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]