FROM python:3.12-alpine
WORKDIR /app
COPY server.py .
EXPOSE 5679
USER nobody
CMD ["python", "-u", "server.py"]
