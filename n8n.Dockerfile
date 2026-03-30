FROM n8nio/n8n
USER root
RUN npm install -g nodemailer
USER node