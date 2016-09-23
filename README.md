#  akka-cluster-custom-downing
[![CircleCI](https://circleci.com/gh/TanUkkii007/akka-cluster-custom-downing.svg?style=svg)](https://circleci.com/gh/TanUkkii007/akka-cluster-custom-downing)

## Introduction

Akka cluster has `akka.cluster.auto-down-unreachable-after` configuration property.
It enables to down unreachable nodes automatically after specified duration.
As [the Akka documentation](http://doc.akka.io/docs/akka/current/scala/cluster-usage.html#Automatic_vs__Manual_Downing) says, 
using auto-down feature is dangerous because it may cause split brain and lead to multiple clusters.
The fundamental problem is that actual node down and temporary network partition cannot be distinguished from each other.
You must realize that auto-downed node may be actually alive and 
one of the worst consequence is that resources that must not be shared are damaged by multiple Cluster Singleton or duplicate entity sharded by Cluster Sharding.

With careful consideration, however, you can use auto-down feature safely for a specific design of distributed application.

If following conditions are met, nodes can be safely down automatically.

1. a node will be isolated from incoming requests if the node is detached from the other cluster members
1. a node mutates shared resources only if it receives a request

How this conditions are applied is vary according to application design.

akka-cluster-custom-downing provides configurable auto-downing strategy you can choose based on your distributed application design.
It lets you configure which nodes can be downed automatically and who is responsible to execute a downing action.

## Status

akka-cluster-custom-downing is currently under development.

## Usage

### LeaderAutoDowningRoles

`LeaderAutoDowningRoles` automatically downs nodes with specified roles.
Like `akka.cluster.AutoDowning`, which is provided with Akka Cluster, a node responsible to down is the leader.

You can enable this strategy with following configuration.

```
akka.cluster.downing-provider-class = "tanukki.akka.cluster.autodown.LeaderAutoDowningRoles"

akka.cluster.auto-down-unreachable-after = 20s

custom-downing {
  leader-auto-downing-roles {
    target-roles = [worker]
  }
}
```


### RoleLeaderAutoDowningRoles

`RoleLeaderAutoDowningRoles` automatically downs nodes with specified roles.
A node responsible to down is the role leader of a specified role.

You can enable this strategy with following configuration.

```
akka.cluster.downing-provider-class = "tanukki.akka.cluster.autodown.RoleLeaderAutoDowningRoles"

akka.cluster.auto-down-unreachable-after = 20s

custom-downing {
  role-leader-auto-downing-roles {
    leader-role = "master"
    target-roles = [worker]
  }
}
```

### OldestAutoDowning

`OldestAutoDowning` automatically downs unreachable members.
A node responsible to down is the oldest member of a specified role.
If `oldest-member-role` is not specified, the oldest member among all cluster members fulfills its duty.

You can enable this strategy with following configuration.

```
akka.cluster.downing-provider-class = "tanukki.akka.cluster.autodown.OldestAutoDowning"

akka.cluster.auto-down-unreachable-after = 20s

custom-downing {
  oldest-auto-downing-roles {
    oldest-member-role = ""
    down-if-alone = true
  }
}
```

Unlike leader based downing strategy, the oldest based downing strategy is much safer.
It is because the oldest member is uniquely determined by all members even if gossip is not converged, 
while different leader might be viewed by members under gossip unconvergence.

Downside of the oldest based downing strategy is loss of downing functionality when the oldest member itself fails.
If `down-if-alone` is set to be true, such scenario can be avoided because the secondary oldest member will down the oldest member if the oldest member get unreachable alone.

### QuorumLeaderAutoDowning

`QuorumLeaderAutoDowning` automatically downs unreachable nodes after specified duration if the number of remaining members are larger than configured `quorum size`.  
This strategy is same as [static quorum](http://doc.akka.io/docs/akka/rp-15v09p02/scala/split-brain-resolver.html#Static_Quorum) strategy of Split Brain Resolver from Typesafe reactive platform.
If `down-if-out-of-quorum` is set to be true, remaining members which number is under quorum size will down themselves.
If `role` is specified, the number of remaining members in the role is used to be compared with quorum size.

```scala

akka.cluster.downing-provider-class = "tanukki.akka.cluster.autodown.QuorumLeaderAutoDowning"

akka.cluster.auto-down-unreachable-after = 20s

custom-downing {
  quorum-leader-auto-downing {
    role = ""
    quorum-size = 0
    down-if-out-of-quorum = true
  }
}

```

## Example