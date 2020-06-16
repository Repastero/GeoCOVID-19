# GeoCOVID-19
Modelo basado en agentes realizado en Repast Simphony

## Instalación
- Descargar e instalar Git:  
https://git-scm.com/downloads

- Ejecutar git desde terminal o MinTTY.
- Cargar credenciales de Github:
```
git config --global user.name "usuario"
git config --global user.email "correo@usuario.com"
```

- Clonar repositorio en workspace de eclipse carpeta GeoCOVID-19:
```
git clone https://github.com/Repastero/GeoCOVID-19.git --branch master GeoCOVID-19
```

## Cómo contribuir
- Buscar una Issue abierta y sin asignados.
- Evaluar si es posible resolver, discutir soluciones, aclarar dudas, etc.
- Asignarse a Issue.
- Crear nueva branch de la principal y actualizar del repositorio remoto:
```
git checkout -b nueva-branch
git pull
```
- Realizar los cambios necesarios en el proyecto para resolver Issue.
- Confirmar cambios en repositorio local y luego actualizar repositoro remoto.
```
git commit -a -m "Descripcion de cambios"
git push origin nueva-branch
```
- En web github crear Pull request de la branch modificada a branch principal (ej: master).
- Escribir los comentarios que se crean necesarios y referenciar Issue.
- Resolver problemas y contestar preguntas que surjan, para que posteriormente se acepte el Pull request.

## Ayuda
- Comandos Git:  
https://git-scm.com/docs
- Cheat sheet (español):  
https://github.github.com/training-kit/downloads/es_ES/github-git-cheat-sheet/

![alt text](https://i.imgur.com/dqiHfIe.jpg)
