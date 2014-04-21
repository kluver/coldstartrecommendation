# Create a chart comparing the algorithms

library("ggplot2")

all.data <- read.csv("eval-user.csv")

nonMetrics = c("Algorithm","Retain", "DataSet", "User", "Partition", "NGood", "TestEvents", "TrainEvents", "NAttempted", "RatingEntropy")

metrics = setdiff(names(all.data),nonMetrics)

for (metric in metrics) {
  plot = ggplot(all.data, aes_string(x="Retain", y=metric, color="Algorithm", shape="Algorithm"))+stat_summary(fun.y=mean, geom="line")+stat_summary(fun.y=mean, geom="point") + stat_summary(fun.data = mean_cl_normal, geom="errorbar")
  metric = gsub('\\.', '_', metric)
  filename = paste(metric,".pdf",sep="")
  cat("Outputting to",filename,"\n")
  pdf(filename, width=5.5, height=4.25)
  print(plot)
  dev.off()
}


dat <- read.csv("eval-results.csv")
plot = ggplot(dat, aes(x=Retain, y=TopN.Entropy, color=Algorithm, shape=Algorithm))+geom_point()+stat_summary(fun.y=mean, geom="line")
pdf("topN_entropy.pdf", width=5.5, height=4.5)
print(plot)
dev.off()
