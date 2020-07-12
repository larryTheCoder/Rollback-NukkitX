# WIP rollback for MCPE
[![Jenkins-CI](https://jenkins.potatohome.xyz/job/Rollback%20Nukkit/badge/icon)](https://jenkins.potatohome.xyz/job/Rollback%20Nukkit)

This plugin requires FastAsyncWorldEdit plugin in order to work correctly. Works for NukkitX
API 1.0.11, MCPE 1.16.x

### Simple
 - This can log block breaking and placement (more events will be added later).
 - Quickly inspect blocks by left clicking with a clock (crouch to inspect the adjacent block)
 - Rollback using a single command `/rollback <user> <radius> <time>`

### Scalable
 - Small database (12MB per million blocks) (MySQL compression)
 - Works well with hundreds of players
 - Lookup and rollback time does not degrade with database size

### Fast
 - Can log hundreds of thousands of blocks a second
 - Rollback millions of blocks without lag
 - Inspect areas instantly
 
### TODO
  - Logging entity / all NBT
 - More events + configuration
 - Translatable messages
